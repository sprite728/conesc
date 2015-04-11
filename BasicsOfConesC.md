# Introduction #

Here we will see, how to implement a simple context aware firmware for TelosB platform. Our program will read a light sensor and send readings to the Base Station (BS). The context-aware functionality depends on the Base Station availability: if its not available, readings are saved in local memory; as soon as BS is available, log is dumped and new readings are sent directly to BS. The source-code can be found here: https://www.dropbox.com/s/3ai4bmg6oaennpc/Demo.zip

Before read this manual, please make sure you have read the nesC manual here: http://tinyos.stanford.edu/tinyos-wiki/index.php/Getting_Started_with_TinyOS

# Main Files #

First of all we need to create main files: main configuration and main module. Nothing special here: it is implemented as just plain nesC source-code. Note that it is important to use `.cnc` extension if you want to use ConesC features in your code. The translator will ignore non-`.cnc` files.

`SMainAppC .cnc`

```
configuration SMainAppC {
} implementation {
  components
    MainC, SMainM, ActiveMessageC, LedsC, 
    new AMReceiverC(AM_BSBEACON),
    new TimerMilliC() as SamplingTimer,
    new TimerMilliC() as BSReset,
    new HamamatsuS1087ParC() as PhotoPar,;

  SMainM.Boot -> MainC;
  SMainM.AMControl -> ActiveMessageC;
  
  SMainM.SamplingTimer -> SamplingTimer;
  SMainM.Light -> PhotoPar;

  SMainM.Leds -> LedsC;
}
```

If you are already familiar with nesC, you probably noticed that we just declared main nesC modules, radio-reciever, leds library, two timers and light-sensor. And of-course wired all of them agains main module.

`SMainM.cnc`
```
#include "constants.h"
#include "types.h"
module SMainM {
  uses interface Boot;
  uses interface SplitControl as AMControl;
  uses interface Timer<TMilli> as SamplingTimer;
  uses interface Read<uint16_t> as Light;
  uses interface Leds;
} implementation {
  nx_uint64_t _timestamp;
  ReportMsg _reportMsg;
//---------------------------Boot routine-------------------------------
  event void Boot.booted() {
    dbg("Debug", "Booted.\n");
    call SamplingTimer.startPeriodic(SAMPLING_INTERVAL);
    // starting radio
    call AMControl.start();
  }
  event void AMControl.startDone(error_t err) {
    if (err != SUCCESS)
      call AMControl.start();
    else {
      dbg("Debug", "Radio started.\n");
    }
  }
  event void AMControl.stopDone(error_t err) {
    if (err != SUCCESS)
      call AMControl.stop();
  }
//--------------------------Internal logic------------------------------
  event void SamplingTimer.fired() {
    call Light.read();
    // incrementing timestamp
    if (_timestamp > 0)
	  _timestamp += SAMPLING_INTERVAL;
  }
  event void Light.readDone(error_t err, uint16_t val) {
    if (err != SUCCESS) return;
    _reportMsg.light = val;
    _reportMsg.timestamp = _timestamp;
    // report
  }
}
```

Ok, this module is fairly simple as well. Here we start timer and radio receiver. Every timeout we read light sensor. As soon as reading is done, we update the value and timestamp in our report data structure, and report it (not really, but we are going to ;) ). Constants and data structure (`ReportMsg`) are declared in `Constants.h` and `types.h` correspondingly.

# Context Aware Behaviour #

Now it is time to add some context aware behaviour to our program. First, we have to think what kind of situations we want our software to adapt against. It is BS availability in our case. It means we have to create _context group_ -- a group of situations with respect to BS. Second, what kind of situations we have with respect to BS. Basically there are to situations: BS reachable and BS unreachable. So we have two _contexts_ -- situations our software may execute in -- `ReachableContext` and `UnreachableContext`. So let's implement them!

`BaseStationG.cnc`
```
#include "types.h"
#include "constants.h"
context group BaseStationG {
  layered void send(ReportMsg *msg);
} implementation {
  components UnreachableCon is default, ReachableCon,
    SenderM, new QueueC(ReportMsg, BUFFER_SIZE), new AMSenderC(AM_BSBEACON),
    LedsC;
    
  UnreachableCon.Sender -> SenderM;
  UnreachableCon.Leds -> LedsC;
  ReachableCon.Sender -> SenderM;
  
  SenderM.Queue -> QueueC;
  SenderM.Packet -> AMSenderC;
  SenderM.AMPacket -> AMSenderC;
  SenderM.AMSend -> AMSenderC;
  SenderM.Leds -> LedsC;
}
```

As you may noticed, _context group_ is an extension of _configuration_ and serves the same purpose: to declare used components (and contexts) and to wire them against each other. We use a separate module `SenderM` in order to isolate and reuse sending functionality between different contexts.

One thing has to be noted: _context group_ is also used to declare a signature of _layered_ function. This function is basically an interface and implemented by contexts included in the group. At runtime ConesC will pick the implementation of this function in _activated_ context. Every context group has to have a _default context_ which will be active at start-up.

First, let's take a look at `Sender` module:

`SenderM.cnc`
```
#include "types.h"
module SenderM {
  provides interface SenderI as Sender;
  uses interface Queue<ReportMsg> as Queue;
  uses interface Packet;
  uses interface AMPacket;
  uses interface AMSend;
  uses interface Leds;
} implementation {
  ReportMsg _msg;
  message_t pkt;
//-------------------Send message routine-------------------------------
  task void send() {
    ReportMsg* rmpkt = (ReportMsg*)(call Packet.getPayload(&pkt, sizeof(ReportMsg)));
    if (rmpkt == NULL) return;
    memcpy(rmpkt, &_msg, sizeof(_msg));
    //call Leds.set(rmpkt->timestamp/1000);
    dbg("Debug","Send log to the BS.\n");
    call AMSend.send(AM_BROADCAST_ADDR, &pkt, sizeof(ReportMsg));
    call Leds.led1Toggle();
  }
  void sendNext() {
    if(call Queue.size() == 0) return;
    _msg = call Queue.dequeue();
    post send();
  }
  event void AMSend.sendDone(message_t* msg, error_t err) {
	if (err == SUCCESS)
	  dbg("Debug","Send done.\n");
    sendNext();
  }
//------------------SenderI implementation------------------------------
  command void Sender.send(ReportMsg *msg) {
    call Queue.enqueue(*msg);
    sendNext();
  }
  command void Sender.dump(ReportMsg *log, nx_uint32_t len) {
    int i = 0;
    for(i = 0; i < len; i++) {
      call Queue.enqueue(log[i]);
    }
  }
}
```

Basically, this module implements an interface, which is used by `ReachableCon` and `UnreachableCon` modules. In addition to the sending routine, there are two functions: `send()` to send _one_ message to BS and `dump()` to send _several_ messages to BS. Next we need to implement the contexts:

`Reachable.cnc`
```
#include "types.h"
context ReachableCon {
  uses interface SenderI as Sender;
} implementation {
  layered void send(ReportMsg *msg) {
    call Sender.send(msg);
  }
}
```

Here we see, `ReachableContext` implements a layered function, which sends the message directly to BS via `Sender` module.

`UnreachableCon.cnc`
```
#include "constants.h"
context UnreachableCon {
  uses interface SenderI as Sender;
  uses interface Leds;
} implementation {
  ReportMsg log[LOG_SIZE];
  nx_uint32_t len, p;
  event void activated() {
    len = 0;
    p = 0;
    call Leds.led1Off();
  }
  event void deactivated() {
    call Sender.dump(log, len);
    call Leds.led2Off();
  }
  layered void send(ReportMsg *msg) {
    memcpy(&log[p], msg, sizeof(log[len]));
    p++;
    if (p>=LOG_SIZE) p = 0;
    if (len<LOG_SIZE) len++;
    call Leds.led2Toggle();
  }
}
```

Instead of sending messages, function `send()` just stores them in internal memory. Here we use `Sender` module as well, but only when this context id _deactivated_: it means that BS is reachable and we have to _dump_ our readings there.

Ok now, when we have a context group and contexts we can use them in our main files. First, we have to add `BaseStationG` to the `components` declaration in our main configuration and wire it against main module `SMainM`.

`SMainAppC.cnc`
```
...
  components
    BaseStationG,
...
SMainM.BaseStationG -> BaseStationG;
...
```

Second, we have to add `BaseStationG` in our main module:

`SMainM.cnc`
```
module SMainM {
  uses context group BaseStationG;
...
  event void Light.readDone(error_t err, uint16_t val) {
    if (err != SUCCESS) return;
    _reportMsg.light = val;
    _reportMsg.timestamp = _timestamp;
    // report
    call BaseStationG.send(&_reportMsg);
  }
...
```

Here programmer doesn't know anything about BS availability, he just uses the functionality provided by the group. But this functionality varies depending on situation: BS available or not.

# Managing Contexts #

Now we use a contexts group, but so far, there is no controller to switch the functionality of that group. Let's create it. We need to listen for the beacons from BS. If we receive the beacon within the timeout, we claim the BS is reachable. BS unreachable thought if timeout occurred.

`BSListenerM.cnc'
```
#include "constants.h"
#include "types.h"
module BSListenerM {
  uses interface Timer<TMilli> as BSReset;
  uses interface Receive;
  uses interface ChronometerI as Chronometer;
  uses context group BaseStationG;
  uses interface Leds;
} implementation {
  event message_t* Receive.receive(message_t* msg, void* payload, uint8_t len) {
	ReportMsg* rmpkt = (ReportMsg*)payload;
	call BSReset.stop();
	call BSReset.startOneShot(BSBEACON_TIMEOUT);
	activate BaseStationG.ReachableCon;
    return msg;
  }
  event void BSReset.fired() {
    activate BaseStationG.UnreachableCon;
  }
}
```

Here we used keyword `activate` in order to switch the functionality. For example, line `activate BaseStationG.UnreachableCon;` means that context `UnreachableCon` will be active and function `BaseStationG.send()` will store readings locally.

Last thing to do is to add the declaration of the module in `SMainAppC.cnc`:

`SMainAppC.cnc`
```
...
  components
    BSListenerM,
...
  BSListenerM.BSReset -> BSReset;
  BSListenerM.Receive -> AMReceiverC;
  BSListenerM.BaseStationG -> BaseStationG;
  BSListenerM.Leds -> LedsC;
```

This is pretty much it, you can find the source-code here: https://www.dropbox.com/s/3ai4bmg6oaennpc/Demo.zip.

Good luck!
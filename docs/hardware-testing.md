# Hardware test plan

Use a parked vehicle in a ventilated area. Record Android model/version, USB chipset, ELM firmware, vehicle/year/protocol, baud, and every result.

Test attach before/after launch, permission approval/denial, connect/disconnect/reconnect, cable removal during read, ignition off, initialization error, malformed response, `NO DATA`, all supported-range masks, each standard PID against trusted equipment, Mode 03, 60-minute foreground operation, trip buffering/export, portrait/landscape, and process recreation. Repeat with FTDI, CP210x, CH340/341, PL2303, and CDC ACM where available.

No physical hardware verification has been performed by repository automation.

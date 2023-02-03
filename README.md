# RS2 File Server

A stand-alone server for distributing rs2 cache files.

## Quick guide

Download the latest [released jar](../../releases/) and [file-server.properties](./file-server.properties) into the same directory.

Update `file-server.properties` with your own values, including cache directory and rsa keys.

> You can remove the `prefetchKeys` property to auto generate them on start-up.

## Building

To build the project, run `./gradlew build` and you'll find a jar called `rs2-file-server.jar` inside `build/libs`.  
You can also load this project in your IDE of choice and build using a gradle task.  

To quickly get up and started you may also run `./gradlew run` which will run it without making a jar.

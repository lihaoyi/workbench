scala-js-workbench
-----------------

!https://github.com/lihaoyi/scala-js-workbench/blob/master/Example.png?raw=true

A SBT plugin for [scala-js](https://github.com/lampepfl/scala-js) projects to make development in the browser more pleasant.

- Spins up a local websocket server on (by default) localhost:12345, whenever you're in the SBT console. Navigate to localhost:12345 in the browser and it'll show a simple page tell you it's alive.
- Generates a `workbench.js` file in your packageJS output directory, which acts a stub for SBT to control the browser. You'll need to include this in your HTML page manually via a script tag.
- Forwards all SBT logging from your SBT console to the browser console, so you can see what's going on (e.g. when the project is recompiling) without having to flip back and forth between browser and terminal.
- Sends commands to tell the connected browsers to refresh every time your Scala.Js project completes a `packageJS`.

To Use
------

- Clone this from Github into a local directory
- Add a dependency onto the scala-js-workbench project, e.g. in `project/project/Build.sbt`
- Add `scala.js.workbench.buildSettingsX` to your project settings in `project/Build.sbt`
- Add `refreshBrowsers <<= refreshBrowsers.triggeredBy(packageJS in Compile)` to your project settings, to make it refresh every time `packageJS` completes.
- Modify the `packageJS` task with the following setting, to make it generate the snippet of `workbench.js` file needed to communicate with SBT:

```scala
packageJS in Compile := {
    (packageJS in Compile).value :+ scala.js.workbench.generateClient.value
  }
```

With that done, when you open a HTML page containing `workbench.js`, if you have sbt running and scala-js-workbench enabled, it should connect over websockets and start forwarding our SBT log to the browser javascript console. You can run the `refreshBrowsers` command to tell it to refresh itself, and if you set up the `triggeredBy` rule as shown above, it should refresh itself automatically at the end of every `packageJS` cycle.

Current still sort of flaky; in particular, it does not behave properly across `reload`s in SBT, so if the refreshes stop working you may need to `exit` and restart SBT.

Depends on [SprayWebSockets](https://github.com/lihaoyi/SprayWebSockets) for its websocket server; this will need to be checked out into a local directory WebSockets next to your SBT project folder. See this repo (https://github.com/lihaoyi/scala-js-game-2) for a usage example.

Pull requests welcome!

License
-------
The MIT License (MIT)

Copyright (c) 2013 Li Haoyi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

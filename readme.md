scala-js-workbench
-----------------

![Example](https://github.com/lihaoyi/scala-js-workbench/blob/master/Example.png?raw=true)

A SBT plugin for [scala-js](https://github.com/lampepfl/scala-js) projects to make development in the browser more pleasant.

- Spins up a local websocket server on (by default) localhost:12345, whenever you're in the SBT console. Navigate to localhost:12345 in the browser and it'll show a simple page tell you it's alive.
- Generates a `workbench.js` file in your packageJS output directory, which acts a stub for SBT to control the browser. You'll need to include this in your HTML page manually via a script tag.
- Forwards all SBT logging from your SBT console to the browser console, so you can see what's going on (e.g. when the project is recompiling) without having to flip back and forth between browser and terminal.
- Sends commands to tell the connected browsers to refresh/update every time your Scala.Js project completes a `packageJS`.

To Use
------

- Clone this from Github into a local directory
- Add a dependency onto the scala-js-workbench project, e.g. in `project/project/Build.sbt`
- Add `scala.js.workbench.buildSettingsX` to your project settings in `project/Build.sbt`
- Modify the `packageJS` task with the following setting, to make it generate the snippet of `workbench.js` file needed to communicate with SBT:

```scala
packageJS in Compile := {
    (packageJS in Compile).value :+ scala.js.workbench.generateClient.value
  }
```

- Define your `bootstrapSnippet`, which is a piece of javascript to be run to start your application, e.g. `bootstrapSnippet := "ScalaJS.modules.example_ScalaJSExample().main();"`. scala-js-workbench requires this so it can use it to re-start your application later on its own. You do not also need to include this on the page itself, as scala-js-workbench will execute this snippet when the browser first connects.

Now you have a choice of what you want to do when the code compiles:

refreshBrowsers
===============
`refreshBrowsers <<= refreshBrowsers.triggeredBy(packageJS in Compile)`

This will to make any client browsers refresh every time `packageJS` completes, saving you flipping back and forth between SBT and the browser to refresh the page after compilation is finished.

updateBrowsers
==============
`updateBrowsers <<= updateBrowsers.triggeredBy(packageJS in Compile)`

This will attempt to perform an update without refreshing the page every time `packageJS` completes, which is much faster than a full page refresh since the browser doesn't need to parse/exec the huge blob of `extdeps.js`. This involves:

- Returning the state of `document.body` to the initial state before any javascript was run
- Stripping all event listeners from things within body
- Clearing all repeated timeouts and intervals.

`updateBrowsers` is a best-effort cleanup, and does not do things like:

- clear up outstanding websocket/ajax connections
- undo modifications done to `window` or `document`
- mutations to global javascript objects

Nonetheless, for the bulk of javascript libraries these limitations are acceptable. As long as you're not doing anything too crazy, `updateBrowsers` but should suffice for most applications.

-------

With that done, when you open a HTML page containing `workbench.js`, if you have sbt running and scala-js-workbench enabled, it should connect over websockets and start forwarding our SBT log to the browser javascript console. You can now run the `refreshBrowsers` and `updateBrowsers` commands to tell it to refresh itself, and if you set up the `triggeredBy` rule as shown above, it should refresh/update itself automatically at the end of every `packageJS` cycle.

Currently still sort of flaky; in particular, it does not behave properly across `reload`s in SBT, so if the refreshes stop working you may need to `exit` and restart SBT. Also, the initial page-load/refresh while the caches are first being set up may cause things to misbehave, but refreshing the page manually once should be enough for it to stabilize.

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

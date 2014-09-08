scala-js-workbench
-----------------

![Example](https://github.com/lihaoyi/scala-js-workbench/blob/master/Example.png?raw=true)

A SBT plugin for [scala-js](https://github.com/lampepfl/scala-js) projects to make development in the browser more pleasant.

- Spins up a local web server on (by default) `localhost:12345`, whenever you're in the SBT console. Navigate to localhost:12345 in the browser and it'll show a simple page tell you it's alive. You can access any file within your project directory by going to `localhost:12345/path/to/file` in a browser.
- Forwards all SBT logging from your SBT console to the browser console, so you can see what's going on (e.g. when the project is recompiling) without having to flip back and forth between browser and terminal.
- Sends commands to tell the connected browsers to refresh/update every time your Scala.Js project completes a `packageJS`.

Check out the [example app](https://github.com/lihaoyi/workbench-example-app) for a plug-and-play example of workbench in action.

Installation
------------

- Add to your `project/plugins.sbt`
```scala
resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.lihaoyi" % "workbench" % "0.2.1")
```
- Add to your `build.sbt`
```scala
workbenchSettings
```
If you're using `project/Build.scala` or similar, also  add:
```scala
import scala.js.workbench.Plugin._
```
- So that workbench knows how to restart your application, specify a `bootSnippet` property in your SBT build, which is a javascript command to start your application, e.g.
```scala
bootSnippet := "ScalaJSExample().main();"
```
- For all web pages you would like to integrate with workbench, add
```html
<script type="text/javascript" src="/workbench.js"></script>
```

### Usage

Once the above installation steps are completed, simply open your desired HTML file via `http://localhost:12345` with the URL path being any file part relative to your project root. e.g. `localhost:12345/target/scala-2.10/classes/index.html`. This should serve up the HTML file and connect it to workbench.


# Live Reloading

You have a choice of what you want to do when the code compiles.

#### refreshBrowsers

```scala
refreshBrowsers <<= refreshBrowsers.triggeredBy(packageJS in Compile)
```

This will to make any client browsers refresh every time `packageJS` completes, saving you flipping back and forth between SBT and the browser to refresh the page after compilation is finished.

#### updateBrowsers

```scala
updateBrowsers <<= updateBrowsers.triggeredBy(packageJS in Compile)
```

This will attempt to perform an update without refreshing the page every time `packageJS` completes, which is much faster than a full page refresh since the browser doesn't need to parse/exec the huge blob of `extdeps.js`. This involves:

- Returning the state of `document.body` to the initial state before any javascript was run
- Stripping all event listeners from things within body
- Clearing all repeated timeouts and intervals
- Running the `bootSnippet` again

`updateBrowsers` is a best-effort cleanup, and does not do things like:

- clear up outstanding websocket/ajax connections
- undo modifications done to `window` or `document`
- mutations to global javascript objects

Nonetheless, for the bulk of javascript libraries these limitations are acceptable. As long as you're not doing anything too crazy, `updateBrowsers` but should suffice for most applications.

You can force the clean-up-and-reboot to happen from the browser via the shortcut Ctrl-Alt-Shift-Enter if you simply wish to reset the browser to a clean state.

-------

With this done, you should be receiving the SBT logspam (compilation, warnings, errors) in your browse console, and the page should be automatically refreshing/updating when the application gets recompiled. If you have problems setting this up, try starting from the [example app](https://github.com/lihaoyi/workbench-example-app) and working from there.


# Development

To develop, go into `example/` and run `sbt ~fastOptJS`. Then you can go to

```
http://localhost:12345/target/scala-2.11/classes/index-dev.html
```

and see a small sierpinski-triangle application. Editing the code within `example/` should cause the SBT log-spam to appear in the browser console, and changes (e.g. changing the color of the background fill) should cause a recompile and updating of the browser animation.

To make changes to workbench, modify the workbench source code and stop/re-run `sbt ~fastOptJS`. When workbench finishes re-compiling, SBT re-starts and the page becomes accessible, your changes to workbench will take effect. You can replace `fullOptJs` in the `built.sbt` file with `fastOptJS`, and swapping the reference to `client-opt.js` to `client-fastopt.js`, if you want to speed up the development cycle.

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

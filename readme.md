workbench 0.3.0
---------------

![Example](https://github.com/lihaoyi/scala-js-workbench/blob/master/Example.png?raw=true)

A SBT plugin for [scala-js](https://github.com/lampepfl/scala-js) projects to make development in the browser more pleasant.

- Spins up a local web server on (by default) `localhost:12345`, whenever you're in the SBT console. Navigate to localhost:12345 in the browser and it'll show a simple page tell you it's alive. You can access any file within your project directory by going to `localhost:12345/path/to/file` in a browser.
- Forwards all SBT logging from your SBT console to the browser console, so you can see what's going on (e.g. when the project is recompiling) without having to flip back and forth between browser and terminal.
- Sends commands to tell the connected browsers to refresh/update every time your Scala.Js project completes a `fastOptJS`.

Check out the [example app](https://github.com/lihaoyi/workbench-example-app) for a plug-and-play example of workbench in action.

Installation
------------
- Add to your `project/plugins.sbt`
```scala
addSbtPlugin("com.lihaoyi" % "workbench" % "0.3.0")
```
- Add to your `build.sbt`
```scala
enablePlugins(WorkbenchPlugin)
```
- For all web pages you would like to integrate with workbench, add
```html
<script type="text/javascript" src="/workbench.js"></script>
```

### Usage

Once the above installation steps are completed, simply open your desired HTML file via `http://localhost:12345` with the URL path being any file part relative to your project root. e.g. `localhost:12345/target/scala-2.12/classes/index.html`. This should serve up the HTML file and connect it to workbench.

#### Server Starting Behaviour

By default, the server starts up when sbt loads. Ie, starting the sbt terminal via `sbt` will start the server as well.
This behaviour can be changed by adding on of the following settings to your `build.sbt`:
- start the server on compilation (eg when running `sbt "~fastOptJS"`)
```scala
workbenchStartMode := WorkbenchStartModes.OnCompile
```
- start the server manually using the `startWorkbenchServer` task
```scala
workbenchStartMode := WorkbenchStartModes.Manual
```

# Live Reloading

You have a choice of what you want to do when the code compiles.

#### Refresh page
This will to make any client browsers refresh every time `fastOptJS` completes, saving you flipping back and forth between SBT and the browser to refresh the page after compilation is finished. This is the default behavior.

#### Splice changes
```scala
// WorkbenchPlugin must NOT be enabled at the same time
enablePlugins(WorkbenchSplicePlugin)
```

This is an experimental feature that aims to perform an update to the code running in the browser *without losing the state of the running code*! Thus you can make changes to the code and have them immediately appear in the program, without having to restart and lose the current state of the application. See [this video](https://vimeo.com/105852957) for a demo of it in action.  

This live splicing is not doable in the general case, but only for some subset of changes:

- Changes inside method bodies
- Adding new `def`s and `lazy val`s to classes/objects
- Creating entirely new classes/objects

This means that there are many changes that `spliceBrowsers` does not support, such as.

- Adding new `val`s and `var`s to classes/objects
- Modifying inheritance hierarchies
- Changing the type of an existing `val`/`var`/`lazy val`
- Renaming classes

And many more. If the change is something that Workbench does not support, you'll see errors in the browser console:

![Example](https://github.com/lihaoyi/scala-js-workbench/blob/master/Error.png?raw=true)

And you'll need to refresh the page.

Note that you have to turn off the Scala.js inliner (as shown in the above SBT snippet) in order to have this work. The inliner performs inlinings across class and method boundaries that makes it hard to predict whether or not the live-splicer will work.

Lastly, note that `spliceBrowsers` does not retroactively modify the state of the application as if the changes in the code had always been present. For example, values set by the constructor in instances of a class will remain as-is even if you modify the class constructor; only new instances will be affected by the modified constructor. 

In general, it is entirely possible to get into weird/invalid states due to this live-splicing, and the general solution is simply to refresh the page.

-------

With this done, you should be receiving the SBT logspam (compilation, warnings, errors) in your browse console, and the page should be automatically refreshing/updating when the application gets recompiled. If you have problems setting this up, try starting from the [example app](https://github.com/lihaoyi/workbench-example-app) and working from there.


# Development

To develop, go into `example/` and run `sbt ~fastOptJS`. Then you can go to

```
http://localhost:12345/target/scala-2.12/classes/index-dev.html
```

and see a small sierpinski-triangle application. Editing the code within `example/` should cause the SBT log-spam to appear in the browser console, and changes (e.g. changing the color of the background fill) should cause a recompile and updating of the browser animation.

To make changes to workbench, modify the workbench source code and stop/re-run `sbt ~fastOptJS`. When workbench finishes re-compiling, SBT re-starts and the page becomes accessible, your changes to workbench will take effect.

Pull requests welcome!

Change Log
----------

##0.3.0
- Migration to AutoPlugins
- Removal of `updateBrowsers` feature, made obsolete by increased speed of `fastOptJS`
- General upgrade of dependencies

##0.2.3

- Upgraded uPickle, removed need for special resolver

##0.2.2

- First implementation of `spliceBrowsers`

##0.2.1

- Added missing resolver `http://dl.bintray.com/non/maven`

##0.2.0

- First implementation of workbench client in Scala.js

##0.1.5

- Properly kill the spray server on plugin unload, `sbt reload` now works 
- Swap out `play-json` with `upickle`
- (Internally) separate Spray server code with SBT madness

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

scala-js-resource
-----------------

Provides common functionality when working with [scala-js](https://github.com/lampepfl/scala-js) applications. Enhances the sbt `packageJS` command to:

- Copies files from your `/src/js` folder directly into the output path
- Bundles up files from your `/src/resource` folder into a single `resource.js` file, and makes them available through the `scala.scalajs.js.Resource` object. For example, calling `Resource("cow.txt").string` will provide you the string contents of `/src/resource/cow.txt`.

Currently really rough around the edges, but it already does much of what I want it to do. Pull requests welcome!

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

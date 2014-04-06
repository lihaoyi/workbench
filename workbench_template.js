(function(){
    var shadowBody = null
    var bootSnippet = "<bootSnippet>"
    window.onload = function(){
        shadowBody = document.body.cloneNode(true)
        start()
    }
    window.addEventListener("keydown", function (event) {
        if(event.keyCode==13 && event.ctrlKey && event.altKey && event.shiftKey) {
            clear()
            eval(bootSnippet)
        }
    })
    function clear(){
        document.body = shadowBody.cloneNode(true)
        for(var i = 0; i < 99999; i++){
            clearTimeout(i)
            clearInterval(i)
        }
    }

    var start = function(){
        var req = new XMLHttpRequest()

        req.open("POST", "http://<host>:<port>/notifications")

        req.onload = function(){
            if (req.status != 200){
                setTimeout(function(){start()}, 1000)
            }else{
                var dataList = JSON.parse(req.responseText)
                for(var i = 0; i < dataList.length; i++){

                    var data = dataList[i]
                    if (data[0] == "reload") {
                        console.log("Reloading page...")
                        location.reload()
                    }
                    if (data[0] == "clear"){
                        clear()
                    }
                    if (data[0] == "run"){
                        var tag = document.createElement("script")
                        var loaded = false
                        tag.setAttribute("src", "http://<host>:<port>" + data[1])
                        var bootSnippet = data[2]
                        if (bootSnippet){
                            tag.onreadystatechange = tag.onload = function() {
                                console.log("Post-run reboot")
                                if (!loaded) {
                                    console.log("Post-run reboot go!")
                                    eval(bootSnippet)
                                }
                                loaded = true
                            };
                        }
                        document.head.appendChild(tag)
                    }
                    if (data[0] == "boot"){
                        eval(bootSnippet)
                    }
                    if (data[0] == "print") console[data[1]](data[2])
                }
                start()
            }
        }
        req.send()
    }
})()

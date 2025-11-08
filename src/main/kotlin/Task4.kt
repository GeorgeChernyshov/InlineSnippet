package org.example

class MyClass {

    private fun doInternalWork() {
        println("MyClass is doing internal work")
    }

    fun triggerInternalAction() {
        println("Triggering internal action from MyClass itself...")
        doSomething {
            doInternalWork()
        }
    }
}

inline fun MyClass.doSomething(block: MyClass.() -> Unit) {
    block()
}
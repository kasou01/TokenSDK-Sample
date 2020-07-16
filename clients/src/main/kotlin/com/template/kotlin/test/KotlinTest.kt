package com.template.kotlin.test

import java.lang.NullPointerException
import java.math.RoundingMode
import java.util.concurrent.locks.Lock

fun main(args : Array<String>) {
    println(215.toBigDecimal().multiply(50.toBigDecimal()).divide(100.toBigDecimal()).setScale(-1,RoundingMode.DOWN).toPlainString())
    println(2239.23.toBigDecimal().setScale(-1,RoundingMode.DOWN))

}

fun <T> lockfun(lock: Lock, body: () -> T): T {
    lock.lock()
    try {
        return body()
    }
    finally {
        lock.unlock()
    }
}

open class A {
    open fun foo(i: Int = 10) :Int{return i }
}

class B : A() {
    override fun foo(i: Int) :Int{     try{return i}finally {
        print("end")
    } }  // デフォルト値は使用できない
}
infix fun Int.plus(x: Int): Int {
    return this.plus(x)
}
interface Base {
    fun print()
}

class BaseImpl(val x: Int) : Base {
    override fun print() { print(x) }
}

class DataProvider
class D
class C {
    fun D.foo() {
        toString()         // D.toString() を呼ぶ
        this@C.toString()  // C.toString() を呼ぶ
    }
}
data class User(val name: String, val age: Int)
enum class Color(val rgb: Int) {
    RED(0xFF0000),
    GREEN(0x00FF00),
    BLUE(0x0000FF)
}
enum class ProtocolState {
    WAITING {
        override fun signal() = TALKING
    },

    TALKING {
        override fun signal() = WAITING
    };

    abstract fun signal(): ProtocolState
}
package de.bjrke.kotlinjs

val jsObject = js("Object")

fun string(name: dynamic) = name as String
fun double(number: dynamic) = number as Double

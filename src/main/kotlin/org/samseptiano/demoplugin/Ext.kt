package org.samseptiano.demoplugin

fun String.toPascalCase(): String {
    return this.trim().split(Regex("[\\s_-]+"))
        .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
}

fun String.capitalizeFirstChar(): String = replaceFirstChar { it.uppercase() }
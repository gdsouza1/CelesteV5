package core.functionality

class AppendableParameter(val prefix: String, val name: String, val isSubpar: Boolean = false, val defaultPar: String?) {
    constructor(prefix: String, name: String) : this(prefix, name, false, null)
    constructor(prefix: String, name: String, isSubpar: Boolean) : this(prefix, name, isSubpar, null)
}
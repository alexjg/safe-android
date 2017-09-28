package pm.gnosis.heimdall.data.exceptions

import pm.gnosis.heimdall.data.model.JsonRpcError

class JsonRpcException(val jsonRpcError: JsonRpcError) : Exception() {
    val errorCode: Int?
        get() = jsonRpcError.code

    override val message: String?
        get() = jsonRpcError.message
}
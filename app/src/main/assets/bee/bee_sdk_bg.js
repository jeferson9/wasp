export class GraphqlBlockData {
    static __wrap(ptr) {
        ptr = ptr >>> 0;
        const obj = Object.create(GraphqlBlockData.prototype);
        obj.__wbg_ptr = ptr;
        GraphqlBlockDataFinalization.register(obj, obj.__wbg_ptr, obj);
        return obj;
    }
    __destroy_into_raw() {
        const ptr = this.__wbg_ptr;
        this.__wbg_ptr = 0;
        GraphqlBlockDataFinalization.unregister(this);
        return ptr;
    }
    free() {
        const ptr = this.__destroy_into_raw();
        wasm.__wbg_graphqlblockdata_free(ptr, 0);
    }
    /**
     * @returns {bigint}
     */
    get seq_no() {
        const ret = wasm.__wbg_get_graphqlblockdata_seq_no(this.__wbg_ptr);
        return BigInt.asUintN(64, ret);
    }
    /**
     * @param {bigint} arg0
     */
    set seq_no(arg0) {
        wasm.__wbg_set_graphqlblockdata_seq_no(this.__wbg_ptr, arg0);
    }
}
if (Symbol.dispose) GraphqlBlockData.prototype[Symbol.dispose] = GraphqlBlockData.prototype.free;

export class Miner {
    static __wrap(ptr) {
        ptr = ptr >>> 0;
        const obj = Object.create(Miner.prototype);
        obj.__wbg_ptr = ptr;
        MinerFinalization.register(obj, obj.__wbg_ptr, obj);
        return obj;
    }
    __destroy_into_raw() {
        const ptr = this.__wbg_ptr;
        this.__wbg_ptr = 0;
        MinerFinalization.unregister(this);
        return ptr;
    }
    free() {
        const ptr = this.__destroy_into_raw();
        wasm.__wbg_miner_free(ptr, 0);
    }
    /**
     * @param {number} x
     * @param {number} y
     */
    add_tap(x, y) {
        const ret = wasm.miner_add_tap(this.__wbg_ptr, x, y);
        if (ret[1]) {
            throw takeFromExternrefTable0(ret[0]);
        }
    }
    /**
     * @returns {boolean}
     */
    can_start() {
        const ret = wasm.miner_can_start(this.__wbg_ptr);
        return ret !== 0;
    }
    /**
     * @returns {Promise<GraphqlBlockData>}
     */
    get_current_block() {
        const ret = wasm.miner_get_current_block(this.__wbg_ptr);
        return ret;
    }
    /**
     * @returns {Promise<MinerAccountData>}
     */
    get_miner_data() {
        const ret = wasm.miner_get_miner_data(this.__wbg_ptr);
        return ret;
    }
    /**
     * @returns {Promise<void>}
     */
    get_reward() {
        const ret = wasm.miner_get_reward(this.__wbg_ptr);
        return ret;
    }
    /**
     * @param {string[]} endpoints
     * @param {string} app_id
     * @param {string} address
     * @param {string} public_key
     * @param {string} secret_key
     * @returns {Promise<Miner>}
     */
    static new(endpoints, app_id, address, public_key, secret_key) {
        const ptr0 = passArrayJsValueToWasm0(endpoints, wasm.__wbindgen_malloc);
        const len0 = WASM_VECTOR_LEN;
        const ptr1 = passStringToWasm0(app_id, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
        const len1 = WASM_VECTOR_LEN;
        const ptr2 = passStringToWasm0(address, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
        const len2 = WASM_VECTOR_LEN;
        const ptr3 = passStringToWasm0(public_key, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
        const len3 = WASM_VECTOR_LEN;
        const ptr4 = passStringToWasm0(secret_key, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
        const len4 = WASM_VECTOR_LEN;
        const ret = wasm.miner_new(ptr0, len0, ptr1, len1, ptr2, len2, ptr3, len3, ptr4, len4);
        return ret;
    }
    /**
     * @param {string} seed
     */
    remove_seed(seed) {
        const ptr0 = passStringToWasm0(seed, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
        const len0 = WASM_VECTOR_LEN;
        wasm.miner_remove_seed(this.__wbg_ptr, ptr0, len0);
    }
    /**
     * @param {number} duration_ms
     * @param {Function} callback
     */
    start(duration_ms, callback) {
        const ret = wasm.miner_start(this.__wbg_ptr, duration_ms, callback);
        if (ret[1]) {
            throw takeFromExternrefTable0(ret[0]);
        }
    }
    stop() {
        wasm.miner_stop(this.__wbg_ptr);
    }
}
if (Symbol.dispose) Miner.prototype[Symbol.dispose] = Miner.prototype.free;

export class MinerAccountData {
    static __wrap(ptr) {
        ptr = ptr >>> 0;
        const obj = Object.create(MinerAccountData.prototype);
        obj.__wbg_ptr = ptr;
        MinerAccountDataFinalization.register(obj, obj.__wbg_ptr, obj);
        return obj;
    }
    __destroy_into_raw() {
        const ptr = this.__wbg_ptr;
        this.__wbg_ptr = 0;
        MinerAccountDataFinalization.unregister(this);
        return ptr;
    }
    free() {
        const ptr = this.__destroy_into_raw();
        wasm.__wbg_mineraccountdata_free(ptr, 0);
    }
    /**
     * @returns {bigint}
     */
    get epoch_5m_start() {
        const ret = wasm.__wbg_get_mineraccountdata_epoch_5m_start(this.__wbg_ptr);
        return BigInt.asUintN(64, ret);
    }
    /**
     * @returns {bigint}
     */
    get epoch_start() {
        const ret = wasm.__wbg_get_mineraccountdata_epoch_start(this.__wbg_ptr);
        return BigInt.asUintN(64, ret);
    }
    /**
     * @returns {bigint}
     */
    get tap_sum_5m() {
        const ret = wasm.__wbg_get_mineraccountdata_tap_sum_5m(this.__wbg_ptr);
        return (BigInt.asUintN(64, ret[0]) | (BigInt.asUintN(64, ret[1]) << BigInt(64)));
    }
    /**
     * @returns {bigint}
     */
    get tap_sum() {
        const ret = wasm.__wbg_get_mineraccountdata_tap_sum(this.__wbg_ptr);
        return (BigInt.asUintN(64, ret[0]) | (BigInt.asUintN(64, ret[1]) << BigInt(64)));
    }
    /**
     * @param {bigint} arg0
     */
    set epoch_5m_start(arg0) {
        wasm.__wbg_set_mineraccountdata_epoch_5m_start(this.__wbg_ptr, arg0);
    }
    /**
     * @param {bigint} arg0
     */
    set epoch_start(arg0) {
        wasm.__wbg_set_mineraccountdata_epoch_start(this.__wbg_ptr, arg0);
    }
    /**
     * @param {bigint} arg0
     */
    set tap_sum_5m(arg0) {
        wasm.__wbg_set_mineraccountdata_tap_sum_5m(this.__wbg_ptr, arg0, arg0 >> BigInt(64));
    }
    /**
     * @param {bigint} arg0
     */
    set tap_sum(arg0) {
        wasm.__wbg_set_mineraccountdata_tap_sum(this.__wbg_ptr, arg0, arg0 >> BigInt(64));
    }
}
if (Symbol.dispose) MinerAccountData.prototype[Symbol.dispose] = MinerAccountData.prototype.free;

export class ResultOfGenMiningKeys {
    static __wrap(ptr) {
        ptr = ptr >>> 0;
        const obj = Object.create(ResultOfGenMiningKeys.prototype);
        obj.__wbg_ptr = ptr;
        ResultOfGenMiningKeysFinalization.register(obj, obj.__wbg_ptr, obj);
        return obj;
    }
    __destroy_into_raw() {
        const ptr = this.__wbg_ptr;
        this.__wbg_ptr = 0;
        ResultOfGenMiningKeysFinalization.unregister(this);
        return ptr;
    }
    free() {
        const ptr = this.__destroy_into_raw();
        wasm.__wbg_resultofgenminingkeys_free(ptr, 0);
    }
    /**
     * @returns {string}
     */
    get deep_link() {
        let deferred1_0;
        let deferred1_1;
        try {
            const ret = wasm.resultofgenminingkeys_deep_link(this.__wbg_ptr);
            deferred1_0 = ret[0];
            deferred1_1 = ret[1];
            return getStringFromWasm0(ret[0], ret[1]);
        } finally {
            wasm.__wbindgen_free(deferred1_0, deferred1_1, 1);
        }
    }
    /**
     * @returns {string}
     */
    get public() {
        let deferred1_0;
        let deferred1_1;
        try {
            const ret = wasm.resultofgenminingkeys_public(this.__wbg_ptr);
            deferred1_0 = ret[0];
            deferred1_1 = ret[1];
            return getStringFromWasm0(ret[0], ret[1]);
        } finally {
            wasm.__wbindgen_free(deferred1_0, deferred1_1, 1);
        }
    }
    /**
     * @returns {string}
     */
    get secret() {
        let deferred1_0;
        let deferred1_1;
        try {
            const ret = wasm.resultofgenminingkeys_secret(this.__wbg_ptr);
            deferred1_0 = ret[0];
            deferred1_1 = ret[1];
            return getStringFromWasm0(ret[0], ret[1]);
        } finally {
            wasm.__wbindgen_free(deferred1_0, deferred1_1, 1);
        }
    }
}
if (Symbol.dispose) ResultOfGenMiningKeys.prototype[Symbol.dispose] = ResultOfGenMiningKeys.prototype.free;

/**
 * @param {TParamsOfEnsureMiningKeysPropagated} params
 * @returns {Promise<void>}
 */
export function ensure_mining_keys_propagated(params) {
    const ret = wasm.ensure_mining_keys_propagated(params);
    return ret;
}

/**
 * @param {string} app_id
 * @returns {Promise<ResultOfGenMiningKeys>}
 */
export function gen_mining_keys(app_id) {
    const ptr0 = passStringToWasm0(app_id, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    const len0 = WASM_VECTOR_LEN;
    const ret = wasm.gen_mining_keys(ptr0, len0);
    return ret;
}

/**
 * @param {TParamsOfGetMinerAddressByWalletName} params
 * @returns {Promise<string>}
 */
export function get_miner_address_by_wallet_name(params) {
    const ret = wasm.get_miner_address_by_wallet_name(params);
    return ret;
}
export function __wbg_Error_83742b46f01ce22d(arg0, arg1) {
    const ret = Error(getStringFromWasm0(arg0, arg1));
    return ret;
}
export function __wbg_Number_a5a435bd7bbec835(arg0) {
    const ret = Number(arg0);
    return ret;
}
export function __wbg_String_8564e559799eccda(arg0, arg1) {
    const ret = String(arg1);
    const ptr1 = passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    const len1 = WASM_VECTOR_LEN;
    getDataViewMemory0().setInt32(arg0 + 4 * 1, len1, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, ptr1, true);
}
export function __wbg_Window_6b1e5e30561398b0(arg0) {
    const ret = arg0.Window;
    return ret;
}
export function __wbg_WorkerGlobalScope_c2be21ef9cc5eb0e(arg0) {
    const ret = arg0.WorkerGlobalScope;
    return ret;
}
export function __wbg___wbindgen_bigint_get_as_i64_447a76b5c6ef7bda(arg0, arg1) {
    const v = arg1;
    const ret = typeof(v) === 'bigint' ? v : undefined;
    getDataViewMemory0().setBigInt64(arg0 + 8 * 1, isLikeNone(ret) ? BigInt(0) : ret, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, !isLikeNone(ret), true);
}
export function __wbg___wbindgen_boolean_get_c0f3f60bac5a78d1(arg0) {
    const v = arg0;
    const ret = typeof(v) === 'boolean' ? v : undefined;
    return isLikeNone(ret) ? 0xFFFFFF : ret ? 1 : 0;
}
export function __wbg___wbindgen_debug_string_5398f5bb970e0daa(arg0, arg1) {
    const ret = debugString(arg1);
    const ptr1 = passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    const len1 = WASM_VECTOR_LEN;
    getDataViewMemory0().setInt32(arg0 + 4 * 1, len1, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, ptr1, true);
}
export function __wbg___wbindgen_in_41dbb8413020e076(arg0, arg1) {
    const ret = arg0 in arg1;
    return ret;
}
export function __wbg___wbindgen_is_bigint_e2141d4f045b7eda(arg0) {
    const ret = typeof(arg0) === 'bigint';
    return ret;
}
export function __wbg___wbindgen_is_function_3c846841762788c1(arg0) {
    const ret = typeof(arg0) === 'function';
    return ret;
}
export function __wbg___wbindgen_is_object_781bc9f159099513(arg0) {
    const val = arg0;
    const ret = typeof(val) === 'object' && val !== null;
    return ret;
}
export function __wbg___wbindgen_is_string_7ef6b97b02428fae(arg0) {
    const ret = typeof(arg0) === 'string';
    return ret;
}
export function __wbg___wbindgen_is_undefined_52709e72fb9f179c(arg0) {
    const ret = arg0 === undefined;
    return ret;
}
export function __wbg___wbindgen_jsval_eq_ee31bfad3e536463(arg0, arg1) {
    const ret = arg0 === arg1;
    return ret;
}
export function __wbg___wbindgen_jsval_loose_eq_5bcc3bed3c69e72b(arg0, arg1) {
    const ret = arg0 == arg1;
    return ret;
}
export function __wbg___wbindgen_number_get_34bb9d9dcfa21373(arg0, arg1) {
    const obj = arg1;
    const ret = typeof(obj) === 'number' ? obj : undefined;
    getDataViewMemory0().setFloat64(arg0 + 8 * 1, isLikeNone(ret) ? 0 : ret, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, !isLikeNone(ret), true);
}
export function __wbg___wbindgen_string_get_395e606bd0ee4427(arg0, arg1) {
    const obj = arg1;
    const ret = typeof(obj) === 'string' ? obj : undefined;
    var ptr1 = isLikeNone(ret) ? 0 : passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    var len1 = WASM_VECTOR_LEN;
    getDataViewMemory0().setInt32(arg0 + 4 * 1, len1, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, ptr1, true);
}
export function __wbg___wbindgen_throw_6ddd609b62940d55(arg0, arg1) {
    throw new Error(getStringFromWasm0(arg0, arg1));
}
export function __wbg__wbg_cb_unref_6b5b6b8576d35cb1(arg0) {
    arg0._wbg_cb_unref();
}
export function __wbg_call_2d781c1f4d5c0ef8() { return handleError(function (arg0, arg1, arg2) {
    const ret = arg0.call(arg1, arg2);
    return ret;
}, arguments); }
export function __wbg_call_e133b57c9155d22c() { return handleError(function (arg0, arg1) {
    const ret = arg0.call(arg1);
    return ret;
}, arguments); }
export function __wbg_clearTimeout_113b1cde814ec762(arg0) {
    const ret = clearTimeout(arg0);
    return ret;
}
export function __wbg_clearTimeout_fdfb5a1468af1a97(arg0, arg1) {
    arg0.clearTimeout(arg1);
}
export function __wbg_close_af26905c832a88cb() { return handleError(function (arg0) {
    arg0.close();
}, arguments); }
export function __wbg_createObjectStore_92a8aebcc6f9d7e3() { return handleError(function (arg0, arg1, arg2) {
    const ret = arg0.createObjectStore(getStringFromWasm0(arg1, arg2));
    return ret;
}, arguments); }
export function __wbg_crypto_38df2bab126b63dc(arg0) {
    const ret = arg0.crypto;
    return ret;
}
export function __wbg_data_a3d9ff9cdd801002(arg0) {
    const ret = arg0.data;
    return ret;
}
export function __wbg_debug_4b9b1a2d5972be57(arg0) {
    console.debug(arg0);
}
export function __wbg_done_08ce71ee07e3bd17(arg0) {
    const ret = arg0.done;
    return ret;
}
export function __wbg_entries_e8a20ff8c9757101(arg0) {
    const ret = Object.entries(arg0);
    return ret;
}
export function __wbg_error_74898554122344a8() { return handleError(function (arg0) {
    const ret = arg0.error;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}, arguments); }
export function __wbg_error_8d9a8e04cd1d3588(arg0) {
    console.error(arg0);
}
export function __wbg_fetch_f8a611684c3b5fe5(arg0, arg1) {
    const ret = arg0.fetch(arg1);
    return ret;
}
export function __wbg_getRandomValues_3f44b700395062e5() { return handleError(function (arg0, arg1) {
    globalThis.crypto.getRandomValues(getArrayU8FromWasm0(arg0, arg1));
}, arguments); }
export function __wbg_getRandomValues_c44a50d8cfdaebeb() { return handleError(function (arg0, arg1) {
    arg0.getRandomValues(arg1);
}, arguments); }
export function __wbg_getTime_1dad7b5386ddd2d9(arg0) {
    const ret = arg0.getTime();
    return ret;
}
export function __wbg_getTimezoneOffset_639bcf2dde21158b(arg0) {
    const ret = arg0.getTimezoneOffset();
    return ret;
}
export function __wbg_get_326e41e095fb2575() { return handleError(function (arg0, arg1) {
    const ret = Reflect.get(arg0, arg1);
    return ret;
}, arguments); }
export function __wbg_get_6ac8c8119f577720() { return handleError(function (arg0, arg1) {
    const ret = arg0.get(arg1);
    return ret;
}, arguments); }
export function __wbg_get_a8ee5c45dabc1b3b(arg0, arg1) {
    const ret = arg0[arg1 >>> 0];
    return ret;
}
export function __wbg_get_unchecked_329cfe50afab7352(arg0, arg1) {
    const ret = arg0[arg1 >>> 0];
    return ret;
}
export function __wbg_get_with_ref_key_6412cf3094599694(arg0, arg1) {
    const ret = arg0[arg1];
    return ret;
}
export function __wbg_global_deb18d05f75c643d(arg0) {
    const ret = arg0.global;
    return ret;
}
export function __wbg_graphqlblockdata_new(arg0) {
    const ret = GraphqlBlockData.__wrap(arg0);
    return ret;
}
export function __wbg_headers_fc8c672cd757e0fd(arg0) {
    const ret = arg0.headers;
    return ret;
}
export function __wbg_indexedDB_2ae2128d487c6ebc() { return handleError(function (arg0) {
    const ret = arg0.indexedDB;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}, arguments); }
export function __wbg_indexedDB_66709c81db8a4d72() { return handleError(function (arg0) {
    const ret = arg0.indexedDB;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}, arguments); }
export function __wbg_indexedDB_c83feb7151bbde52() { return handleError(function (arg0) {
    const ret = arg0.indexedDB;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}, arguments); }
export function __wbg_instanceof_ArrayBuffer_101e2bf31071a9f6(arg0) {
    let result;
    try {
        result = arg0 instanceof ArrayBuffer;
    } catch (_) {
        result = false;
    }
    const ret = result;
    return ret;
}
export function __wbg_instanceof_Error_4691a5b466e32a80(arg0) {
    let result;
    try {
        result = arg0 instanceof Error;
    } catch (_) {
        result = false;
    }
    const ret = result;
    return ret;
}
export function __wbg_instanceof_Response_9b4d9fd451e051b1(arg0) {
    let result;
    try {
        result = arg0 instanceof Response;
    } catch (_) {
        result = false;
    }
    const ret = result;
    return ret;
}
export function __wbg_instanceof_Uint8Array_740438561a5b956d(arg0) {
    let result;
    try {
        result = arg0 instanceof Uint8Array;
    } catch (_) {
        result = false;
    }
    const ret = result;
    return ret;
}
export function __wbg_instanceof_Window_23e677d2c6843922(arg0) {
    let result;
    try {
        result = arg0 instanceof Window;
    } catch (_) {
        result = false;
    }
    const ret = result;
    return ret;
}
export function __wbg_isArray_33b91feb269ff46e(arg0) {
    const ret = Array.isArray(arg0);
    return ret;
}
export function __wbg_isSafeInteger_ecd6a7f9c3e053cd(arg0) {
    const ret = Number.isSafeInteger(arg0);
    return ret;
}
export function __wbg_item_f0d01dd089cc05ba(arg0, arg1, arg2) {
    const ret = arg1.item(arg2 >>> 0);
    var ptr1 = isLikeNone(ret) ? 0 : passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    var len1 = WASM_VECTOR_LEN;
    getDataViewMemory0().setInt32(arg0 + 4 * 1, len1, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, ptr1, true);
}
export function __wbg_iterator_d8f549ec8fb061b1() {
    const ret = Symbol.iterator;
    return ret;
}
export function __wbg_length_b3416cf66a5452c8(arg0) {
    const ret = arg0.length;
    return ret;
}
export function __wbg_length_ea16607d7b61445b(arg0) {
    const ret = arg0.length;
    return ret;
}
export function __wbg_message_00d63f20c41713dd(arg0) {
    const ret = arg0.message;
    return ret;
}
export function __wbg_message_e959edc81e4b6cb7(arg0, arg1) {
    const ret = arg1.message;
    const ptr1 = passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    const len1 = WASM_VECTOR_LEN;
    getDataViewMemory0().setInt32(arg0 + 4 * 1, len1, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, ptr1, true);
}
export function __wbg_miner_new(arg0) {
    const ret = Miner.__wrap(arg0);
    return ret;
}
export function __wbg_mineraccountdata_new(arg0) {
    const ret = MinerAccountData.__wrap(arg0);
    return ret;
}
export function __wbg_msCrypto_bd5a034af96bcba6(arg0) {
    const ret = arg0.msCrypto;
    return ret;
}
export function __wbg_new_0_1dcafdf5e786e876() {
    const ret = new Date();
    return ret;
}
export function __wbg_new_5f486cdf45a04d78(arg0) {
    const ret = new Uint8Array(arg0);
    return ret;
}
export function __wbg_new_ab79df5bd7c26067() {
    const ret = new Object();
    return ret;
}
export function __wbg_new_dd50bcc3f60ba434() { return handleError(function (arg0, arg1) {
    const ret = new WebSocket(getStringFromWasm0(arg0, arg1));
    return ret;
}, arguments); }
export function __wbg_new_fd94ca5c9639abd2(arg0) {
    const ret = new Date(arg0);
    return ret;
}
export function __wbg_new_typed_aaaeaf29cf802876(arg0, arg1) {
    try {
        var state0 = {a: arg0, b: arg1};
        var cb0 = (arg0, arg1) => {
            const a = state0.a;
            state0.a = 0;
            try {
                return wasm_bindgen__convert__closures_____invoke__h6375f47a1b16748b(a, state0.b, arg0, arg1);
            } finally {
                state0.a = a;
            }
        };
        const ret = new Promise(cb0);
        return ret;
    } finally {
        state0.a = state0.b = 0;
    }
}
export function __wbg_new_with_length_825018a1616e9e55(arg0) {
    const ret = new Uint8Array(arg0 >>> 0);
    return ret;
}
export function __wbg_new_with_str_299114bdb2430303() { return handleError(function (arg0, arg1, arg2, arg3) {
    const ret = new WebSocket(getStringFromWasm0(arg0, arg1), getStringFromWasm0(arg2, arg3));
    return ret;
}, arguments); }
export function __wbg_new_with_str_and_init_b4b54d1a819bc724() { return handleError(function (arg0, arg1, arg2) {
    const ret = new Request(getStringFromWasm0(arg0, arg1), arg2);
    return ret;
}, arguments); }
export function __wbg_next_11b99ee6237339e3() { return handleError(function (arg0) {
    const ret = arg0.next();
    return ret;
}, arguments); }
export function __wbg_next_e01a967809d1aa68(arg0) {
    const ret = arg0.next;
    return ret;
}
export function __wbg_node_84ea875411254db1(arg0) {
    const ret = arg0.node;
    return ret;
}
export function __wbg_now_16f0c993d5dd6c27() {
    const ret = Date.now();
    return ret;
}
export function __wbg_objectStoreNames_564985d2e9ae7523(arg0) {
    const ret = arg0.objectStoreNames;
    return ret;
}
export function __wbg_objectStore_f314ab152a5c7bd0() { return handleError(function (arg0, arg1, arg2) {
    const ret = arg0.objectStore(getStringFromWasm0(arg1, arg2));
    return ret;
}, arguments); }
export function __wbg_open_f3dc09caa3990bc4() { return handleError(function (arg0, arg1, arg2) {
    const ret = arg0.open(getStringFromWasm0(arg1, arg2));
    return ret;
}, arguments); }
export function __wbg_process_44c7a14e11e9f69e(arg0) {
    const ret = arg0.process;
    return ret;
}
export function __wbg_prototypesetcall_d62e5099504357e6(arg0, arg1, arg2) {
    Uint8Array.prototype.set.call(getArrayU8FromWasm0(arg0, arg1), arg2);
}
export function __wbg_put_f1673d719f93ce22() { return handleError(function (arg0, arg1, arg2) {
    const ret = arg0.put(arg1, arg2);
    return ret;
}, arguments); }
export function __wbg_queueMicrotask_0c399741342fb10f(arg0) {
    const ret = arg0.queueMicrotask;
    return ret;
}
export function __wbg_queueMicrotask_a082d78ce798393e(arg0) {
    queueMicrotask(arg0);
}
export function __wbg_randomFillSync_6c25eac9869eb53c() { return handleError(function (arg0, arg1) {
    arg0.randomFillSync(arg1);
}, arguments); }
export function __wbg_random_5bb86cae65a45bf6() {
    const ret = Math.random();
    return ret;
}
export function __wbg_readyState_57fa0866477cc0c4(arg0) {
    const ret = arg0.readyState;
    return (__wbindgen_enum_IdbRequestReadyState.indexOf(ret) + 1 || 3) - 1;
}
export function __wbg_require_b4edbdcf3e2a1ef0() { return handleError(function () {
    const ret = module.require;
    return ret;
}, arguments); }
export function __wbg_resolve_ae8d83246e5bcc12(arg0) {
    const ret = Promise.resolve(arg0);
    return ret;
}
export function __wbg_result_c5baa2d3d690a01a() { return handleError(function (arg0) {
    const ret = arg0.result;
    return ret;
}, arguments); }
export function __wbg_resultofgenminingkeys_new(arg0) {
    const ret = ResultOfGenMiningKeys.__wrap(arg0);
    return ret;
}
export function __wbg_send_4a1dc66e8653e5ed() { return handleError(function (arg0, arg1, arg2) {
    arg0.send(getStringFromWasm0(arg1, arg2));
}, arguments); }
export function __wbg_setTimeout_7f7035ad0b026458() { return handleError(function (arg0, arg1, arg2) {
    const ret = arg0.setTimeout(arg1, arg2);
    return ret;
}, arguments); }
export function __wbg_setTimeout_ef24d2fc3ad97385() { return handleError(function (arg0, arg1) {
    const ret = setTimeout(arg0, arg1);
    return ret;
}, arguments); }
export function __wbg_set_body_a3d856b097dfda04(arg0, arg1) {
    arg0.body = arg1;
}
export function __wbg_set_e09648bea3f1af1e() { return handleError(function (arg0, arg1, arg2, arg3, arg4) {
    arg0.set(getStringFromWasm0(arg1, arg2), getStringFromWasm0(arg3, arg4));
}, arguments); }
export function __wbg_set_method_8c015e8bcafd7be1(arg0, arg1, arg2) {
    arg0.method = getStringFromWasm0(arg1, arg2);
}
export function __wbg_set_onabort_63885d8d7841a8d5(arg0, arg1) {
    arg0.onabort = arg1;
}
export function __wbg_set_onblocked_04591dedaff404d2(arg0, arg1) {
    arg0.onblocked = arg1;
}
export function __wbg_set_oncomplete_f31e6dc6d16c1ff8(arg0, arg1) {
    arg0.oncomplete = arg1;
}
export function __wbg_set_onerror_8a268cb237177bba(arg0, arg1) {
    arg0.onerror = arg1;
}
export function __wbg_set_onerror_901ca711f94a5bbb(arg0, arg1) {
    arg0.onerror = arg1;
}
export function __wbg_set_onerror_c1ecd6233c533c08(arg0, arg1) {
    arg0.onerror = arg1;
}
export function __wbg_set_onmessage_6f80ab771bf151aa(arg0, arg1) {
    arg0.onmessage = arg1;
}
export function __wbg_set_onopen_34e3e24cf9337ddd(arg0, arg1) {
    arg0.onopen = arg1;
}
export function __wbg_set_onsuccess_fca94ded107b64af(arg0, arg1) {
    arg0.onsuccess = arg1;
}
export function __wbg_set_onupgradeneeded_860ce42184f987e7(arg0, arg1) {
    arg0.onupgradeneeded = arg1;
}
export function __wbg_set_onversionchange_3d88930f82c97b92(arg0, arg1) {
    arg0.onversionchange = arg1;
}
export function __wbg_static_accessor_GLOBAL_8adb955bd33fac2f() {
    const ret = typeof global === 'undefined' ? null : global;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}
export function __wbg_static_accessor_GLOBAL_THIS_ad356e0db91c7913() {
    const ret = typeof globalThis === 'undefined' ? null : globalThis;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}
export function __wbg_static_accessor_SELF_f207c857566db248() {
    const ret = typeof self === 'undefined' ? null : self;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}
export function __wbg_static_accessor_WINDOW_bb9f1ba69d61b386() {
    const ret = typeof window === 'undefined' ? null : window;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}
export function __wbg_status_318629ab93a22955(arg0) {
    const ret = arg0.status;
    return ret;
}
export function __wbg_stringify_5ae93966a84901ac() { return handleError(function (arg0) {
    const ret = JSON.stringify(arg0);
    return ret;
}, arguments); }
export function __wbg_subarray_a068d24e39478a8a(arg0, arg1, arg2) {
    const ret = arg0.subarray(arg1 >>> 0, arg2 >>> 0);
    return ret;
}
export function __wbg_target_7bc90f314634b37b(arg0) {
    const ret = arg0.target;
    return isLikeNone(ret) ? 0 : addToExternrefTable0(ret);
}
export function __wbg_text_372f5b91442c50f9() { return handleError(function (arg0) {
    const ret = arg0.text();
    return ret;
}, arguments); }
export function __wbg_then_098abe61755d12f6(arg0, arg1) {
    const ret = arg0.then(arg1);
    return ret;
}
export function __wbg_then_9e335f6dd892bc11(arg0, arg1, arg2) {
    const ret = arg0.then(arg1, arg2);
    return ret;
}
export function __wbg_transaction_1309b463c399d2b3() { return handleError(function (arg0, arg1, arg2, arg3) {
    const ret = arg0.transaction(getStringFromWasm0(arg1, arg2), __wbindgen_enum_IdbTransactionMode[arg3]);
    return ret;
}, arguments); }
export function __wbg_url_7fefc1820fba4e0c(arg0, arg1) {
    const ret = arg1.url;
    const ptr1 = passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    const len1 = WASM_VECTOR_LEN;
    getDataViewMemory0().setInt32(arg0 + 4 * 1, len1, true);
    getDataViewMemory0().setInt32(arg0 + 4 * 0, ptr1, true);
}
export function __wbg_value_21fc78aab0322612(arg0) {
    const ret = arg0.value;
    return ret;
}
export function __wbg_versions_276b2795b1c6a219(arg0) {
    const ret = arg0.versions;
    return ret;
}
export function __wbindgen_cast_0000000000000001(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 2105, function: Function { arguments: [], shim_idx: 2106, ret: Unit, inner_ret: Some(Unit) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h06f846990d52f1f5, wasm_bindgen__convert__closures_____invoke__h031d30cfd7fa24b5);
    return ret;
}
export function __wbindgen_cast_0000000000000002(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 2931, function: Function { arguments: [NamedExternref("Event")], shim_idx: 2951, ret: Unit, inner_ret: Some(Unit) }, mutable: false }) -> Externref`.
    const ret = makeClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h361a08777c31efd6, wasm_bindgen__convert__closures_____invoke__h219ead9f5d637a83);
    return ret;
}
export function __wbindgen_cast_0000000000000003(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 2931, function: Function { arguments: [], shim_idx: 2950, ret: Unit, inner_ret: Some(Unit) }, mutable: false }) -> Externref`.
    const ret = makeClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h361a08777c31efd6, wasm_bindgen__convert__closures_____invoke__he842bac70fbfd4aa);
    return ret;
}
export function __wbindgen_cast_0000000000000004(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 4008, function: Function { arguments: [Externref], shim_idx: 3602, ret: Unit, inner_ret: Some(Unit) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h0313bbfac724917e, wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8);
    return ret;
}
export function __wbindgen_cast_0000000000000005(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 4008, function: Function { arguments: [NamedExternref("Event")], shim_idx: 3602, ret: Unit, inner_ret: Some(Unit) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h0313bbfac724917e, wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8_4);
    return ret;
}
export function __wbindgen_cast_0000000000000006(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 4008, function: Function { arguments: [NamedExternref("IDBVersionChangeEvent")], shim_idx: 3603, ret: Result(Unit), inner_ret: Some(Result(Unit)) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h0313bbfac724917e, wasm_bindgen__convert__closures_____invoke__hd9a32a0f8882ff92);
    return ret;
}
export function __wbindgen_cast_0000000000000007(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 4008, function: Function { arguments: [NamedExternref("MessageEvent")], shim_idx: 3602, ret: Unit, inner_ret: Some(Unit) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h0313bbfac724917e, wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8_6);
    return ret;
}
export function __wbindgen_cast_0000000000000008(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 594, function: Function { arguments: [], shim_idx: 595, ret: Unit, inner_ret: Some(Unit) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__hbee134c80eaaef2f, wasm_bindgen__convert__closures_____invoke__hf34a5377d3412323);
    return ret;
}
export function __wbindgen_cast_0000000000000009(arg0, arg1) {
    // Cast intrinsic for `Closure(Closure { dtor_idx: 597, function: Function { arguments: [Externref], shim_idx: 130, ret: Result(Unit), inner_ret: Some(Result(Unit)) }, mutable: true }) -> Externref`.
    const ret = makeMutClosure(arg0, arg1, wasm.wasm_bindgen__closure__destroy__h64214a9da0e5ea7b, wasm_bindgen__convert__closures_____invoke__h895fa5ea11123acb);
    return ret;
}
export function __wbindgen_cast_000000000000000a(arg0) {
    // Cast intrinsic for `F64 -> Externref`.
    const ret = arg0;
    return ret;
}
export function __wbindgen_cast_000000000000000b(arg0, arg1) {
    // Cast intrinsic for `Ref(Slice(U8)) -> NamedExternref("Uint8Array")`.
    const ret = getArrayU8FromWasm0(arg0, arg1);
    return ret;
}
export function __wbindgen_cast_000000000000000c(arg0, arg1) {
    // Cast intrinsic for `Ref(String) -> Externref`.
    const ret = getStringFromWasm0(arg0, arg1);
    return ret;
}
export function __wbindgen_cast_000000000000000d(arg0) {
    // Cast intrinsic for `U64 -> Externref`.
    const ret = BigInt.asUintN(64, arg0);
    return ret;
}
export function __wbindgen_init_externref_table() {
    const table = wasm.__wbindgen_externrefs;
    const offset = table.grow(4);
    table.set(0, undefined);
    table.set(offset + 0, undefined);
    table.set(offset + 1, null);
    table.set(offset + 2, true);
    table.set(offset + 3, false);
}
function wasm_bindgen__convert__closures_____invoke__h031d30cfd7fa24b5(arg0, arg1) {
    wasm.wasm_bindgen__convert__closures_____invoke__h031d30cfd7fa24b5(arg0, arg1);
}

function wasm_bindgen__convert__closures_____invoke__he842bac70fbfd4aa(arg0, arg1) {
    wasm.wasm_bindgen__convert__closures_____invoke__he842bac70fbfd4aa(arg0, arg1);
}

function wasm_bindgen__convert__closures_____invoke__hf34a5377d3412323(arg0, arg1) {
    wasm.wasm_bindgen__convert__closures_____invoke__hf34a5377d3412323(arg0, arg1);
}

function wasm_bindgen__convert__closures_____invoke__h219ead9f5d637a83(arg0, arg1, arg2) {
    wasm.wasm_bindgen__convert__closures_____invoke__h219ead9f5d637a83(arg0, arg1, arg2);
}

function wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8(arg0, arg1, arg2) {
    wasm.wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8(arg0, arg1, arg2);
}

function wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8_4(arg0, arg1, arg2) {
    wasm.wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8_4(arg0, arg1, arg2);
}

function wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8_6(arg0, arg1, arg2) {
    wasm.wasm_bindgen__convert__closures_____invoke__h4fda1d53fcab20b8_6(arg0, arg1, arg2);
}

function wasm_bindgen__convert__closures_____invoke__hd9a32a0f8882ff92(arg0, arg1, arg2) {
    const ret = wasm.wasm_bindgen__convert__closures_____invoke__hd9a32a0f8882ff92(arg0, arg1, arg2);
    if (ret[1]) {
        throw takeFromExternrefTable0(ret[0]);
    }
}

function wasm_bindgen__convert__closures_____invoke__h895fa5ea11123acb(arg0, arg1, arg2) {
    const ret = wasm.wasm_bindgen__convert__closures_____invoke__h895fa5ea11123acb(arg0, arg1, arg2);
    if (ret[1]) {
        throw takeFromExternrefTable0(ret[0]);
    }
}

function wasm_bindgen__convert__closures_____invoke__h6375f47a1b16748b(arg0, arg1, arg2, arg3) {
    wasm.wasm_bindgen__convert__closures_____invoke__h6375f47a1b16748b(arg0, arg1, arg2, arg3);
}


const __wbindgen_enum_IdbRequestReadyState = ["pending", "done"];


const __wbindgen_enum_IdbTransactionMode = ["readonly", "readwrite", "versionchange", "readwriteflush", "cleanup"];
const GraphqlBlockDataFinalization = (typeof FinalizationRegistry === 'undefined')
    ? { register: () => {}, unregister: () => {} }
    : new FinalizationRegistry(ptr => wasm.__wbg_graphqlblockdata_free(ptr >>> 0, 1));
const MinerFinalization = (typeof FinalizationRegistry === 'undefined')
    ? { register: () => {}, unregister: () => {} }
    : new FinalizationRegistry(ptr => wasm.__wbg_miner_free(ptr >>> 0, 1));
const MinerAccountDataFinalization = (typeof FinalizationRegistry === 'undefined')
    ? { register: () => {}, unregister: () => {} }
    : new FinalizationRegistry(ptr => wasm.__wbg_mineraccountdata_free(ptr >>> 0, 1));
const ResultOfGenMiningKeysFinalization = (typeof FinalizationRegistry === 'undefined')
    ? { register: () => {}, unregister: () => {} }
    : new FinalizationRegistry(ptr => wasm.__wbg_resultofgenminingkeys_free(ptr >>> 0, 1));

function addToExternrefTable0(obj) {
    const idx = wasm.__externref_table_alloc();
    wasm.__wbindgen_externrefs.set(idx, obj);
    return idx;
}

const CLOSURE_DTORS = (typeof FinalizationRegistry === 'undefined')
    ? { register: () => {}, unregister: () => {} }
    : new FinalizationRegistry(state => state.dtor(state.a, state.b));

function debugString(val) {
    // primitive types
    const type = typeof val;
    if (type == 'number' || type == 'boolean' || val == null) {
        return  `${val}`;
    }
    if (type == 'string') {
        return `"${val}"`;
    }
    if (type == 'symbol') {
        const description = val.description;
        if (description == null) {
            return 'Symbol';
        } else {
            return `Symbol(${description})`;
        }
    }
    if (type == 'function') {
        const name = val.name;
        if (typeof name == 'string' && name.length > 0) {
            return `Function(${name})`;
        } else {
            return 'Function';
        }
    }
    // objects
    if (Array.isArray(val)) {
        const length = val.length;
        let debug = '[';
        if (length > 0) {
            debug += debugString(val[0]);
        }
        for(let i = 1; i < length; i++) {
            debug += ', ' + debugString(val[i]);
        }
        debug += ']';
        return debug;
    }
    // Test for built-in
    const builtInMatches = /\[object ([^\]]+)\]/.exec(toString.call(val));
    let className;
    if (builtInMatches && builtInMatches.length > 1) {
        className = builtInMatches[1];
    } else {
        // Failed to match the standard '[object ClassName]'
        return toString.call(val);
    }
    if (className == 'Object') {
        // we're a user defined class or Object
        // JSON.stringify avoids problems with cycles, and is generally much
        // easier than looping through ownProperties of `val`.
        try {
            return 'Object(' + JSON.stringify(val) + ')';
        } catch (_) {
            return 'Object';
        }
    }
    // errors
    if (val instanceof Error) {
        return `${val.name}: ${val.message}\n${val.stack}`;
    }
    // TODO we could test for more things here, like `Set`s and `Map`s.
    return className;
}

function getArrayU8FromWasm0(ptr, len) {
    ptr = ptr >>> 0;
    return getUint8ArrayMemory0().subarray(ptr / 1, ptr / 1 + len);
}

let cachedDataViewMemory0 = null;
function getDataViewMemory0() {
    if (cachedDataViewMemory0 === null || cachedDataViewMemory0.buffer.detached === true || (cachedDataViewMemory0.buffer.detached === undefined && cachedDataViewMemory0.buffer !== wasm.memory.buffer)) {
        cachedDataViewMemory0 = new DataView(wasm.memory.buffer);
    }
    return cachedDataViewMemory0;
}

function getStringFromWasm0(ptr, len) {
    ptr = ptr >>> 0;
    return decodeText(ptr, len);
}

let cachedUint8ArrayMemory0 = null;
function getUint8ArrayMemory0() {
    if (cachedUint8ArrayMemory0 === null || cachedUint8ArrayMemory0.byteLength === 0) {
        cachedUint8ArrayMemory0 = new Uint8Array(wasm.memory.buffer);
    }
    return cachedUint8ArrayMemory0;
}

function handleError(f, args) {
    try {
        return f.apply(this, args);
    } catch (e) {
        const idx = addToExternrefTable0(e);
        wasm.__wbindgen_exn_store(idx);
    }
}

function isLikeNone(x) {
    return x === undefined || x === null;
}

function makeClosure(arg0, arg1, dtor, f) {
    const state = { a: arg0, b: arg1, cnt: 1, dtor };
    const real = (...args) => {

        // First up with a closure we increment the internal reference
        // count. This ensures that the Rust closure environment won't
        // be deallocated while we're invoking it.
        state.cnt++;
        try {
            return f(state.a, state.b, ...args);
        } finally {
            real._wbg_cb_unref();
        }
    };
    real._wbg_cb_unref = () => {
        if (--state.cnt === 0) {
            state.dtor(state.a, state.b);
            state.a = 0;
            CLOSURE_DTORS.unregister(state);
        }
    };
    CLOSURE_DTORS.register(real, state, state);
    return real;
}

function makeMutClosure(arg0, arg1, dtor, f) {
    const state = { a: arg0, b: arg1, cnt: 1, dtor };
    const real = (...args) => {

        // First up with a closure we increment the internal reference
        // count. This ensures that the Rust closure environment won't
        // be deallocated while we're invoking it.
        state.cnt++;
        const a = state.a;
        state.a = 0;
        try {
            return f(a, state.b, ...args);
        } finally {
            state.a = a;
            real._wbg_cb_unref();
        }
    };
    real._wbg_cb_unref = () => {
        if (--state.cnt === 0) {
            state.dtor(state.a, state.b);
            state.a = 0;
            CLOSURE_DTORS.unregister(state);
        }
    };
    CLOSURE_DTORS.register(real, state, state);
    return real;
}

function passArrayJsValueToWasm0(array, malloc) {
    const ptr = malloc(array.length * 4, 4) >>> 0;
    for (let i = 0; i < array.length; i++) {
        const add = addToExternrefTable0(array[i]);
        getDataViewMemory0().setUint32(ptr + 4 * i, add, true);
    }
    WASM_VECTOR_LEN = array.length;
    return ptr;
}

function passStringToWasm0(arg, malloc, realloc) {
    if (realloc === undefined) {
        const buf = cachedTextEncoder.encode(arg);
        const ptr = malloc(buf.length, 1) >>> 0;
        getUint8ArrayMemory0().subarray(ptr, ptr + buf.length).set(buf);
        WASM_VECTOR_LEN = buf.length;
        return ptr;
    }

    let len = arg.length;
    let ptr = malloc(len, 1) >>> 0;

    const mem = getUint8ArrayMemory0();

    let offset = 0;

    for (; offset < len; offset++) {
        const code = arg.charCodeAt(offset);
        if (code > 0x7F) break;
        mem[ptr + offset] = code;
    }
    if (offset !== len) {
        if (offset !== 0) {
            arg = arg.slice(offset);
        }
        ptr = realloc(ptr, len, len = offset + arg.length * 3, 1) >>> 0;
        const view = getUint8ArrayMemory0().subarray(ptr + offset, ptr + len);
        const ret = cachedTextEncoder.encodeInto(arg, view);

        offset += ret.written;
        ptr = realloc(ptr, len, offset, 1) >>> 0;
    }

    WASM_VECTOR_LEN = offset;
    return ptr;
}

function takeFromExternrefTable0(idx) {
    const value = wasm.__wbindgen_externrefs.get(idx);
    wasm.__externref_table_dealloc(idx);
    return value;
}

let cachedTextDecoder = new TextDecoder('utf-8', { ignoreBOM: true, fatal: true });
cachedTextDecoder.decode();
const MAX_SAFARI_DECODE_BYTES = 2146435072;
let numBytesDecoded = 0;
function decodeText(ptr, len) {
    numBytesDecoded += len;
    if (numBytesDecoded >= MAX_SAFARI_DECODE_BYTES) {
        cachedTextDecoder = new TextDecoder('utf-8', { ignoreBOM: true, fatal: true });
        cachedTextDecoder.decode();
        numBytesDecoded = len;
    }
    return cachedTextDecoder.decode(getUint8ArrayMemory0().subarray(ptr, ptr + len));
}

const cachedTextEncoder = new TextEncoder();

if (!('encodeInto' in cachedTextEncoder)) {
    cachedTextEncoder.encodeInto = function (arg, view) {
        const buf = cachedTextEncoder.encode(arg);
        view.set(buf);
        return {
            read: arg.length,
            written: buf.length
        };
    };
}

let WASM_VECTOR_LEN = 0;


let wasm;
export function __wbg_set_wasm(val) {
    wasm = val;
}

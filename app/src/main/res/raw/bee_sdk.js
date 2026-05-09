/* @ts-self-types="./bee_sdk.d.ts" */

import * as wasm from "./bee_sdk_bg.wasm";
import { __wbg_set_wasm } from "./bee_sdk_bg.js";
__wbg_set_wasm(wasm);
wasm.__wbindgen_start();
export {
    GraphqlBlockData, Miner, MinerAccountData, ResultOfGenMiningKeys, ensure_mining_keys_propagated, gen_mining_keys, get_miner_address_by_wallet_name
} from "./bee_sdk_bg.js";

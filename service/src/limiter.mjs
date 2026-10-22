import {ApiError} from './errors.mjs';
export class LoginLimiter {
  constructor({now=Date.now}={}) { this.now=now; this.buckets=new Map(); }
  consume(key,limit) {
    const now=this.now();
    for(const [k,v] of this.buckets) if(v.expires<=now) this.buckets.delete(k);
    let bucket=this.buckets.get(key);
    if(!bucket) {
      if(this.buckets.size>=5000) throw new ApiError(429,'LOGIN_RATE_LIMITED','Terlalu banyak percobaan. Coba lagi nanti.');
      bucket={count:0,expires:now+60000};this.buckets.set(key,bucket);
    }
    if(++bucket.count>limit) throw new ApiError(429,'LOGIN_RATE_LIMITED','Terlalu banyak percobaan. Coba lagi nanti.');
  }
}

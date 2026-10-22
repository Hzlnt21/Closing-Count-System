import {randomBytes, scrypt as scryptCallback, timingSafeEqual} from 'node:crypto';
import {promisify} from 'node:util';
import {invalid} from './errors.mjs';
const scrypt=promisify(scryptCallback);
export function validatePassword(password) {
  if(typeof password!=='string' || [...password].length<12 || Buffer.byteLength(password)>256) {
    throw invalid('Password memerlukan minimal 12 karakter dan maksimal 256 byte.');
  }
}
export async function hashPassword(password) {
  return hashPasswordWithSalt(password,randomBytes(16).toString('hex'));
}
export async function hashPasswordWithSalt(password,salt) {
  validatePassword(password);
  if(!/^[a-f0-9]{32}$/.test(salt))throw invalid();
  const key=await scrypt(password,salt,64,{N:16384,r:8,p:1,maxmem:32*1024*1024});
  return `scrypt$v1$${salt}$${key.toString('hex')}`;
}
export async function verifyPassword(password,stored) {
  const match=typeof stored==='string' && /^scrypt\$v1\$([a-f0-9]{32})\$([a-f0-9]{128})$/.exec(stored);
  if(!match || typeof password!=='string' || Buffer.byteLength(password)>256) return false;
  const key=await scrypt(password,match[1],64,{N:16384,r:8,p:1,maxmem:32*1024*1024});
  return timingSafeEqual(key,Buffer.from(match[2],'hex'));
}
export function normalizeUsername(value) {
  if(typeof value!=='string') throw invalid();
  const normalized=value.trim().toLowerCase();
  if(!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(normalized)) throw invalid('Username harus 3-64 karakter huruf, angka, titik, garis bawah atau tanda hubung.');
  return normalized;
}

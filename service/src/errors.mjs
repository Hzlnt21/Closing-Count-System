export class ApiError extends Error {
  constructor(status, code, message) { super(message); this.status=status; this.code=code; }
}
export const invalid = (message='Data yang dikirim tidak valid.') => new ApiError(400,'VALIDATION_ERROR',message);
export const forbidden = () => new ApiError(403,'OUTLET_FORBIDDEN','Akses outlet tidak diizinkan.');
export const sessionRequired = () => new ApiError(401,'SESSION_REQUIRED','Silakan login kembali.');
export function exactObject(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== keys.length || !keys.every(k=>Object.hasOwn(value,k))) throw invalid();
}
export const isUuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

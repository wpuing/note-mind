/**
 * 用户端本地登录态：Token / 用户信息存 localStorage（与 Admin 端隔离）。
 */
const TOKEN_KEY = 'notemind_web_token'
const USER_KEY = 'notemind_web_user'

/** 登录后缓存的用户摘要 */
export type WebUser = {
  id: string
  username: string
  nickname: string
  role: string
}

/** 读取 JWT；未登录返回 null */
export function getWebToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 写入 JWT */
export function setWebToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除 Token 与用户缓存（退出登录） */
export function clearWebToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 读取缓存的用户信息；解析失败返回 null */
export function getWebUser(): WebUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    // 有缓存则反序列化，否则视为未登录
    return raw ? (JSON.parse(raw) as WebUser) : null
  } catch {
    // JSON 损坏时当作未登录
    return null
  }
}

/** 缓存用户信息（与 Token 一并在登录成功后写入） */
export function setWebUser(user: WebUser) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

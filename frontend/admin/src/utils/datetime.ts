/** 统一时间展示：yyyy-MM-dd HH:mm:ss（去掉 T、毫秒） */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const normalized = value.trim().replace('T', ' ').replace(/\.\d+.*$/, '').replace(/Z$/i, '')
  const m = normalized.match(
    /^(\d{4}-\d{2}-\d{2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?/,
  )
  if (!m) return normalized || '—'
  const date = m[1]
  const hh = m[2].padStart(2, '0')
  const mm = m[3]
  const ss = (m[4] ?? '00').padStart(2, '0')
  return `${date}\u00A0${hh}:${mm}:${ss}`
}

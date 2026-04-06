function stripTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function resolveRuntimeBaseUrl() {
  if (typeof document === 'undefined') {
    return ''
  }

  const contextPath = stripTrailingSlash(new URL('.', document.baseURI).pathname)
  return contextPath === '/' ? '' : contextPath
}

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const API_BASE_URL = configuredBaseUrl ? stripTrailingSlash(configuredBaseUrl) : resolveRuntimeBaseUrl()

export async function apiRequest<T>(
  path: string,
  options: { method?: string; token?: string | null; body?: unknown } = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    let message = 'Something went wrong'
    try {
      const payload = (await response.json()) as { message?: string }
      message = payload.message ?? message
    } catch {
      message = response.statusText || message
    }
    throw new Error(message)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export async function apiTextRequest(
  path: string,
  options: { method?: string; token?: string | null; body?: unknown } = {},
): Promise<string> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    let message = 'Something went wrong'
    try {
      const payload = (await response.json()) as { message?: string }
      message = payload.message ?? message
    } catch {
      message = response.statusText || message
    }
    throw new Error(message)
  }

  return response.text()
}

export function createQuery(params: Record<string, string | number | boolean | null | undefined>) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.set(key, String(value))
    }
  })
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 204) {
    return null as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  const rawBody = await response.text()
  if (response.status === 202 && rawBody.trim() === '') {
    return null as T
  }

  let payload: unknown = rawBody
  if (contentType.includes('application/json')) {
    try {
      payload = JSON.parse(rawBody) as unknown
    } catch {
      throw new ApiError('Invalid JSON response', response.status)
    }
  }

  if (!response.ok) {
    const errorPayload = typeof payload === 'object' && payload !== null
      ? payload as { message?: unknown; error?: unknown }
      : null
    const message = errorPayload
      ? String(errorPayload.message ?? errorPayload.error ?? 'Request failed')
      : 'Request failed'
    throw new ApiError(message, response.status)
  }

  return payload as T
}

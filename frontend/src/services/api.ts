/**
 * Centralized API Client Configuration
 * Base URL configured via VITE_API_BASE_URL environment variable.
 */

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export class ApiError extends Error {
  public status: number;
  public details?: unknown;

  constructor(message: string, status: number, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

interface FetchOptions extends RequestInit {
  timeoutMs?: number;
}

/**
 * Universal fetch wrapper with timeout support, error parsing, and CORS awareness.
 */
export async function apiClient<T>(
  endpoint: string,
  options: FetchOptions = {}
): Promise<T> {
  const { timeoutMs = 12000, ...customConfig } = options;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
  const url = `${API_BASE_URL}${cleanEndpoint}`;

  const headers = new Headers({
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...(customConfig.headers || {}),
  });

  const token = localStorage.getItem('stockguard_token');
  if (token && token !== 'null' && token !== 'undefined' && token.trim() !== '' && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token.trim()}`);
  }

  try {
    const response = await fetch(url, {
      ...customConfig,
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      let errorMessage = `HTTP error ${response.status}: ${response.statusText}`;
      let errorDetails: unknown = null;

      try {
        const errorJson = await response.json();
        errorDetails = errorJson;
        if (errorJson.message) {
          errorMessage = errorJson.message;
        }
      } catch {
        // Response body was not JSON
      }

      throw new ApiError(errorMessage, response.status, errorDetails);
    }

    return (await response.json()) as T;
  } catch (error: unknown) {
    clearTimeout(timeoutId);

    if (error instanceof ApiError) {
      throw error;
    }

    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError(
        'Request timed out. If running on free-tier hosting (Render), the backend container may be cold-starting (30-60s).',
        408
      );
    }

    if (error instanceof TypeError && error.message.includes('Failed to fetch')) {
      throw new ApiError(
        'Unable to reach the StockGuard backend service. Verify that the Spring Boot server is running on ' +
          API_BASE_URL +
          ' and that CORS allows this origin.',
        503
      );
    }

    throw new ApiError(
      error instanceof Error ? error.message : 'Unknown communication error occurred',
      500
    );
  }
}

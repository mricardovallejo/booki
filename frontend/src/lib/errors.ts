export function getErrorMessage(err: unknown, fallback = 'Could not complete the request.'): string {
  return (err as { response?: { data?: { error?: string } } })?.response?.data?.error || fallback;
}

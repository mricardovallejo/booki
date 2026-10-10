import { useCallback, useEffect, useState } from 'react';
import { getDocument } from '../api/documents';
import { getErrorMessage } from '../lib/errors';
import type { Document } from '../types';

export function useDocument(documentId: number | undefined) {
  const [document, setDocument] = useState<Document | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    if (!documentId) return Promise.resolve();
    return getDocument(documentId)
      .then((result) => {
        setDocument(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load this document.')));
  }, [documentId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { document, error, refresh };
}

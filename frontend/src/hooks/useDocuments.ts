import { useCallback, useEffect, useState } from 'react';
import { deleteDocument, listDocuments, uploadDocument } from '../api/documents';
import type { Document } from '../types';

export function useDocuments() {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [isUploading, setIsUploading] = useState(false);

  const refresh = useCallback(() => listDocuments().then(setDocuments), []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const upload = useCallback(
    async (file: File) => {
      setIsUploading(true);
      try {
        await uploadDocument(file);
        await refresh();
      } finally {
        setIsUploading(false);
      }
    },
    [refresh]
  );

  const remove = useCallback(
    async (id: number) => {
      await deleteDocument(id);
      await refresh();
    },
    [refresh]
  );

  return { documents, isUploading, upload, remove, refresh };
}

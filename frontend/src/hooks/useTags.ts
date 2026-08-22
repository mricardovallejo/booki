import { useCallback, useEffect, useState } from 'react';
import {
  addDocumentToTag,
  createTag,
  deleteTag,
  listTags,
  removeDocumentFromTag,
  renameTag
} from '../api/tags';
import { getErrorMessage } from '../lib/errors';
import type { Tag } from '../types';

export function useTags() {
  const [tags, setTags] = useState<Tag[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(
    () =>
      listTags()
        .then((result) => {
          setTags(result);
          setError(null);
        })
        .catch((err) => setError(getErrorMessage(err, 'Could not load your tags.'))),
    []
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  const create = useCallback(
    async (name: string) => {
      await createTag({ name });
      await refresh();
    },
    [refresh]
  );

  const rename = useCallback(
    async (id: number, name: string) => {
      await renameTag(id, name);
      await refresh();
    },
    [refresh]
  );

  const remove = useCallback(
    async (id: number) => {
      await deleteTag(id);
      await refresh();
    },
    [refresh]
  );

  const toggleDocument = useCallback(
    async (tagId: number, documentId: number, isTagged: boolean) => {
      if (isTagged) {
        await removeDocumentFromTag(tagId, documentId);
      } else {
        await addDocumentToTag(tagId, documentId);
      }
      await refresh();
    },
    [refresh]
  );

  return { tags, error, create, rename, remove, toggleDocument, refresh };
}

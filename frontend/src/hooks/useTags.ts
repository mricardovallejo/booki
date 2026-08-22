import { useCallback, useEffect, useState } from 'react';
import {
  addDocumentToTag,
  createTag,
  deleteTag,
  listTags,
  removeDocumentFromTag,
  renameTag
} from '../api/tags';
import type { Tag } from '../types';

export function useTags() {
  const [tags, setTags] = useState<Tag[]>([]);

  const refresh = useCallback(() => listTags().then(setTags).catch(() => setTags([])), []);

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

  return { tags, create, rename, remove, toggleDocument, refresh };
}

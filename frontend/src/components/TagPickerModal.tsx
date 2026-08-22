import type { Document, Tag } from '../types';
import Button from './ui/Button';

interface Props {
  document: Document | null;
  tags: Tag[];
  onToggle: (tagId: number, documentId: number, isTagged: boolean) => void;
  onClose: () => void;
}

export default function TagPickerModal({ document, tags, onToggle, onClose }: Props) {
  if (!document) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-2xl bg-booki-surface p-6 shadow-2xl">
        <h2 className="text-lg font-bold text-white">Organize</h2>
        <p className="mt-1 truncate text-sm text-booki-muted">{document.title}</p>

        <div className="mt-4 max-h-72 space-y-2 overflow-y-auto">
          {tags.length === 0 && (
            <p className="text-sm text-booki-muted">
              You don't have any tags yet. Create one from the library page first.
            </p>
          )}
          {tags.map((tag) => {
            const isTagged = tag.documentIds.includes(document.id);
            return (
              <label
                key={tag.id}
                className="flex cursor-pointer items-center justify-between rounded-lg bg-booki-card px-3 py-2 text-sm text-white transition hover:bg-booki-card-hover"
              >
                <span>{tag.name}</span>
                <input
                  type="checkbox"
                  checked={isTagged}
                  onChange={() => onToggle(tag.id, document.id, isTagged)}
                  className="h-4 w-4 rounded border-white/20 bg-booki-bg text-booki-accent focus:ring-booki-accent"
                />
              </label>
            );
          })}
        </div>

        <Button variant="secondary" onClick={onClose} className="mt-5 w-full">
          Done
        </Button>
      </div>
    </div>
  );
}

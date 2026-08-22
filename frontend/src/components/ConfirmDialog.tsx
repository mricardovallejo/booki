import Button from './ui/Button';

interface Props {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Delete',
  onConfirm,
  onCancel
}: Props) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-2xl bg-booki-surface p-6 shadow-2xl">
        <h2 className="text-lg font-bold text-white">{title}</h2>
        {description && <p className="mt-2 text-sm text-booki-muted">{description}</p>}
        <div className="mt-5 flex gap-3">
          <Button variant="secondary" onClick={onCancel} className="flex-1">
            Cancel
          </Button>
          <Button
            onClick={onConfirm}
            className="flex-1 !bg-rose-600 hover:!bg-rose-500"
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}

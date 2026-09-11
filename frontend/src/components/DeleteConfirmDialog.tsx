import { AlertTriangle, X } from 'lucide-react'

interface DeleteConfirmDialogProps { title: string; description: string; isBusy?: boolean; onConfirm: () => void; onCancel: () => void }

export function DeleteConfirmDialog({ title, description, isBusy = false, onConfirm, onCancel }: DeleteConfirmDialogProps) {
  return <div className="dialog-backdrop" role="presentation" onMouseDown={onCancel}>
    <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-title" onMouseDown={(event) => event.stopPropagation()}>
      <button className="icon-button dialog-close" type="button" aria-label="Close confirmation" onClick={onCancel}><X size={18} /></button>
      <span className="dialog-icon"><AlertTriangle size={20} /></span>
      <p className="section-kicker">Confirm action</p><h2 id="delete-title">{title}</h2><p>{description}</p>
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onCancel} disabled={isBusy}>Cancel</button><button className="danger-button" type="button" onClick={onConfirm} disabled={isBusy}>{isBusy ? 'Deleting...' : 'Delete document'}</button></div>
    </div>
  </div>
}

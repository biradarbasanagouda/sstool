import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Signal, Plus, Radio, Trash2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { propertiesApi } from '@/api/properties'
import api from '@/api/client'
import Modal from '@/components/common/Modal'
import EmptyState from '@/components/common/EmptyState'
import PageHeader from '@/components/common/PageHeader'
import Spinner from '@/components/common/Spinner'
import Badge from '@/components/common/Badge'
import toast from 'react-hot-toast'
import { format } from 'date-fns'

const TOOLS = ['VISTUMBLER','KISMET','SPLAT','MANUAL','OTHER'] as const

const toolColor: Record<string, 'blue' | 'green' | 'yellow' | 'slate'> = {
  VISTUMBLER: 'green', KISMET: 'blue', SPLAT: 'yellow', MANUAL: 'slate', OTHER: 'slate'
}

export default function RfScansPage() {
  const orgId = useAuthStore(s => s.orgId)
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [selectedProperty, setSelectedProperty] = useState<number | null>(null)

  const { data: propertiesData } = useQuery({
    queryKey: ['properties', orgId],
    queryFn: () => propertiesApi.list(orgId),
  })

  const { data: scans, isLoading } = useQuery({
    queryKey: ['rf-scans', selectedProperty],
    queryFn: () => selectedProperty
      ? api.get<any[]>('/rf-scans', { params: { propertyId: selectedProperty } }).then(r => r.data)
      : Promise.resolve([] as any[]),
    enabled: !!selectedProperty,
  })

  const { register, handleSubmit, reset } = useForm<{
    propertyId: number
    tool: string
    notes: string
    signalStrength: string
  }>()

  const createMutation = useMutation({
    mutationFn: (d: any) => api.post('/rf-scans', {
      propertyId: Number(d.propertyId),
      tool: d.tool,
      notes: d.notes,
      parsedJson: d.signalStrength ? JSON.stringify({ signalStrength: d.signalStrength }) : null
    }).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rf-scans'] })
      toast.success('RF Scan logged')
      setOpen(false)
      reset()
    },
    onError: () => toast.error('Failed to log scan'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/rf-scans/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rf-scans'] })
      toast.success('Scan deleted')
    },
    onError: () => toast.error('Failed to delete scan'),
  })

  const getSignalStrength = (parsedJson: string) => {
    if (!parsedJson) return null
    try {
      const parsed = JSON.parse(parsedJson)
      return parsed.signalStrength || null
    } catch { return null }
  }

  const getSignalColor = (strength: string) => {
    const val = parseInt(strength)
    if (val >= -50) return 'text-green-400'
    if (val >= -70) return 'text-yellow-400'
    return 'text-red-400'
  }

  return (
    <div className="space-y-6 animate-slide-up">
      <PageHeader
        title="RF Scans"
        description="Upload and manage radio frequency coverage data"
        action={<button onClick={() => setOpen(true)} className="btn-primary"><Plus size={16}/>Log Scan</button>}
      />

      <div className="flex gap-2 flex-wrap">
        {propertiesData?.content?.map(p => (
          <button key={p.id}
            onClick={() => setSelectedProperty(p.id)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-all duration-150 border
              ${selectedProperty === p.id
                ? 'bg-brand-600/20 border-brand-600/50 text-brand-300'
                : 'bg-surface-muted border-surface-border text-slate-400 hover:text-slate-200'}`}>
            {p.name}
          </button>
        ))}
      </div>

      {!selectedProperty ? (
        <EmptyState icon={<Signal size={36}/>} title="Select a property" description="Choose a property above to view its RF scans" />
      ) : isLoading ? (
        <div className="flex justify-center py-16"><Spinner size={32}/></div>
      ) : !scans?.length ? (
        <EmptyState icon={<Radio size={36}/>} title="No RF scans yet"
          action={<button onClick={() => setOpen(true)} className="btn-primary"><Plus size={16}/>Log Scan</button>} />
      ) : (
        <div className="space-y-3">
          {scans.map((s: any) => {
            const signalStrength = getSignalStrength(s.parsedJson)
            return (
              <div key={s.id} className="card p-5 flex items-center gap-4 group">
                <div className="p-3 bg-orange-600/15 rounded-xl shrink-0">
                  <Signal size={20} className="text-orange-400"/>
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-medium text-white">Scan #{s.id}</span>
                    <Badge label={s.tool} variant={toolColor[s.tool] ?? 'slate'}/>
                    {s.floorLabel && (
                      <span className="text-xs text-slate-500">Floor: {s.floorLabel}</span>
                    )}
                  </div>
                  {signalStrength && (
                    <p className={`text-sm font-mono ${getSignalColor(signalStrength)}`}>
                      Signal: {signalStrength} dBm
                    </p>
                  )}
                  {s.notes && (
                    <p className="text-sm text-slate-400 mt-1">{s.notes}</p>
                  )}
                  <p className="text-xs text-slate-500 mt-1">
                    {s.createdAt ? format(new Date(s.createdAt), 'dd MMM yyyy, HH:mm') : 'Just now'}
                  </p>
                </div>
                <button
                  onClick={() => deleteMutation.mutate(s.id)}
                  className="text-slate-600 hover:text-red-400 transition-colors opacity-0 group-hover:opacity-100">
                  <Trash2 size={15}/>
                </button>
              </div>
            )
          })}
        </div>
      )}

      <Modal title="Log RF Scan" open={open} onClose={() => { setOpen(false); reset() }}>
        <form onSubmit={handleSubmit((d) => createMutation.mutate(d))} className="space-y-4">
          <div>
            <label className="label">Property *</label>
            <select {...register('propertyId', { required: true })} className="input">
              <option value="">Select property...</option>
              {propertiesData?.content?.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Tool *</label>
            <select {...register('tool', { required: true })} className="input">
              {TOOLS.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label className="label">Signal Strength (dBm)</label>
            <input
              {...register('signalStrength')}
              className="input"
              placeholder="e.g. -65"
              type="number"
            />
          </div>
          <div>
            <label className="label">Notes</label>
            <textarea
              {...register('notes')}
              className="input"
              rows={3}
              placeholder="Coverage observations, dead zones, access point locations..."
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={() => setOpen(false)} className="btn-ghost">Cancel</button>
            <button type="submit" disabled={createMutation.isPending} className="btn-primary">
              {createMutation.isPending ? 'Saving...' : 'Log Scan'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
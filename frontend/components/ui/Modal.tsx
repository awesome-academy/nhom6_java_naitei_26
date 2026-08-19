'use client'

import React, { useEffect } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title?: string
  description?: string
  children: React.ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
  closeButton?: boolean
}

const sizeClasses = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
}

function Modal({
  isOpen,
  onClose,
  title,
  description,
  children,
  size = 'md',
  closeButton = true,
}: ModalProps) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = 'unset'
    }

    return () => {
      document.body.style.overflow = 'unset'
    }
  }, [isOpen])

  if (!isOpen) return null

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-black/50 transition-opacity duration-200"
        onClick={onClose}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Escape') onClose()
        }}
      />

      {/* Modal */}
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div
          className={cn(
            'bg-white rounded-lg shadow-lg w-full',
            sizeClasses[size],
            'max-h-[90vh] overflow-y-auto'
          )}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-start justify-between border-b border-neutral-200 p-6">
            <div>
              {title && <h2 className="text-lg font-semibold text-neutral-900">{title}</h2>}
              {description && <p className="text-sm text-neutral-500 mt-1">{description}</p>}
            </div>
            {closeButton && (
              <button
                onClick={onClose}
                className="ml-4 inline-flex items-center justify-center text-neutral-500 hover:text-neutral-700 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>

          {/* Content */}
          <div className="p-6">{children}</div>
        </div>
      </div>
    </>
  )
}

export { Modal }

'use client'

import React, { useState, useRef, useEffect } from 'react'
import { cn } from '@/lib/utils'

export interface DropdownItem {
  label: string
  value: string | number
  onClick?: () => void
  variant?: 'default' | 'danger'
  icon?: React.ReactNode
}

export interface DropdownProps {
  trigger: React.ReactNode
  items: DropdownItem[]
  align?: 'left' | 'right'
  className?: string
}

function Dropdown({ trigger, items, align = 'left', className }: DropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  const handleItemClick = (item: DropdownItem) => {
    item.onClick?.()
    setIsOpen(false)
  }

  return (
    <div ref={dropdownRef} className={cn('relative inline-block', className)}>
      <button onClick={() => setIsOpen(!isOpen)} className="w-full">
        {trigger}
      </button>

      {isOpen && (
        <div
          className={cn(
            'absolute top-full mt-2 bg-white rounded-lg border border-neutral-200 shadow-lg z-50',
            align === 'right' ? 'right-0' : 'left-0',
            'min-w-[200px]'
          )}
        >
          <div className="py-1">
            {items.map((item, index) => (
              <button
                key={`${item.value}-${index}`}
                onClick={() => handleItemClick(item)}
                className={cn(
                  'w-full text-left px-4 py-2 text-sm flex items-center gap-2 transition-colors',
                  item.variant === 'danger'
                    ? 'text-error-600 hover:bg-error-50'
                    : 'text-neutral-900 hover:bg-neutral-100'
                )}
              >
                {item.icon && <span className="w-4 h-4">{item.icon}</span>}
                {item.label}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export { Dropdown }

import React from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  helperText?: string
  options?: Array<{ value: string | number; label: string; disabled?: boolean }>
  placeholder?: string
}

const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, helperText, options = [], placeholder, ...props }, ref) => (
    <div className="w-full">
      {label && (
        <label className="block text-sm font-medium text-neutral-700 mb-2">
          {label}
          {props.required && <span className="text-error-600 ml-1">*</span>}
        </label>
      )}
      <div className="relative">
        <select
          className={cn(
            'flex h-10 w-full appearance-none rounded-md border border-neutral-300 bg-white px-3 py-2 pr-10 text-base',
            'placeholder:text-neutral-400 disabled:cursor-not-allowed disabled:bg-neutral-100 disabled:text-neutral-500',
            'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent',
            'transition-colors duration-200',
            error && 'border-error-500 focus:ring-error-500',
            className
          )}
          ref={ref}
          {...props}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value} disabled={opt.disabled}>
              {opt.label}
            </option>
          ))}
        </select>
        <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-500 pointer-events-none" />
      </div>
      {error && <p className="text-error-600 text-sm mt-1">{error}</p>}
      {helperText && !error && <p className="text-neutral-500 text-sm mt-1">{helperText}</p>}
    </div>
  )
)

Select.displayName = 'Select'

export { Select }

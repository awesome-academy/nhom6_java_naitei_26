import React from 'react'
import { cn } from '@/lib/utils'

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  helperText?: string
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = 'text', label, error, helperText, ...props }, ref) => (
    <div className="w-full">
      {label && (
        <label className="block text-sm font-medium text-neutral-700 mb-2">
          {label}
          {props.required && <span className="text-error-600 ml-1">*</span>}
        </label>
      )}
      <input
        type={type}
        className={cn(
          'flex h-10 w-full rounded-md border border-neutral-300 bg-white px-3 py-2 text-base',
          'placeholder:text-neutral-400 disabled:cursor-not-allowed disabled:bg-neutral-100 disabled:text-neutral-500',
          'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent',
          'transition-colors duration-200',
          error && 'border-error-500 focus:ring-error-500',
          className
        )}
        ref={ref}
        {...props}
      />
      {error && <p className="text-error-600 text-sm mt-1">{error}</p>}
      {helperText && !error && <p className="text-neutral-500 text-sm mt-1">{helperText}</p>}
    </div>
  )
)

Input.displayName = 'Input'

export { Input }

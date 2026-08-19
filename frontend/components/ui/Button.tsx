import React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium ring-offset-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-primary-600 text-white hover:bg-primary-700 active:bg-primary-800',
        secondary: 'bg-neutral-200 text-neutral-900 hover:bg-neutral-300 active:bg-neutral-400',
        success: 'bg-success-600 text-white hover:bg-success-700 active:bg-success-800',
        warning: 'bg-warning-600 text-white hover:bg-warning-700 active:bg-warning-800',
        error: 'bg-error-600 text-white hover:bg-error-700 active:bg-error-800',
        outline: 'border border-neutral-300 text-neutral-900 hover:bg-neutral-50 active:bg-neutral-100',
        ghost: 'text-neutral-900 hover:bg-neutral-100 active:bg-neutral-200',
        link: 'text-primary-600 underline-offset-4 hover:underline active:text-primary-700',
      },
      size: {
        sm: 'h-8 px-3 text-xs',
        md: 'h-10 px-4 text-sm',
        lg: 'h-12 px-6 text-base',
        icon: 'h-10 w-10',
      },
      fullWidth: {
        true: 'w-full',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'md',
      fullWidth: false,
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, fullWidth, ...props }, ref) => (
    <button
      className={cn(buttonVariants({ variant, size, fullWidth, className }))}
      ref={ref}
      {...props}
    />
  )
)

Button.displayName = 'Button'

export { Button, buttonVariants }

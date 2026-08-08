import { cn } from "@/lib/utils";

type BrandMarkProps = {
  className?: string;
  size?: "sm" | "md";
};

/** CSS wordmark matching the logo letterforms (rounded, bent “l”). */
export function BrandMark({ className, size = "md" }: BrandMarkProps) {
  return (
    <span
      className={cn(
        "brand-wordmark inline-flex items-baseline whitespace-nowrap text-logo-red",
        size === "sm" && "text-[1.05rem] leading-none",
        size === "md" && "text-[1.2rem] leading-none sm:text-[1.35rem]",
        className,
      )}
      aria-hidden={false}
    >
      ne
      <span className="brand-bent-l" aria-hidden="true">
        l
      </span>
      sen&nbsp;savannah
    </span>
  );
}

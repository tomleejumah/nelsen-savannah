import { cn } from "@/lib/utils";

type BrandMarkProps = {
  className?: string;
  size?: "sm" | "md";
};

/** Wordmark: Baloo 2 + skewed SVG “l” (brand letterform). */
export function BrandMark({ className, size = "md" }: BrandMarkProps) {
  return (
    <span
      className={cn(
        "brand-logo inline-flex items-end whitespace-nowrap",
        size === "sm" && "brand-logo--sm",
        size === "md" && "brand-logo--md",
        className,
      )}
      aria-label="nelsen savannah"
    >
      <span className="brand-logo-text">ne</span>
      <svg
        className="brand-l-shape"
        viewBox="0 0 46 148"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
        focusable="false"
      >
        <path
          d="
            M 4 0
            C 4 0, 4 95, 4 108
            C 4 132, 18 146, 38 144
            C 48 143, 50 133, 46 126
            C 42 119, 33 122, 28 117
            C 25 113, 25 108, 25 100
            L 25 0
            Z"
          fill="currentColor"
        />
      </svg>
      <span className="brand-logo-text">sen&nbsp;savannah</span>
    </span>
  );
}

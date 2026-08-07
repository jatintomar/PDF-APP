import React from "react";
import { FileText, ArrowDown, Settings, Briefcase, Shuffle } from "lucide-react";

interface AppLogoProps {
  className?: string;
  size?: "sm" | "md" | "lg" | "xl";
}

export const AppLogo: React.FC<AppLogoProps> = ({ className = "", size = "md" }) => {
  const sizeClasses = {
    sm: "w-8 h-8 rounded-lg text-xs",
    md: "w-10 h-10 rounded-xl text-sm",
    lg: "w-16 h-16 rounded-2xl text-lg",
    xl: "w-24 h-24 rounded-3xl text-2xl"
  };

  const containerSize = sizeClasses[size] || sizeClasses.md;

  return (
    <div
      id="app-logo-premium"
      className={`relative flex items-center justify-center overflow-hidden bg-gradient-to-b from-[#3B82F6] via-[#1D4ED8] to-[#1E3A8A] text-white shadow-md select-none border border-blue-400/30 ${containerSize} ${className}`}
      style={{
        boxShadow: "0 8px 16px -4px rgba(29, 78, 216, 0.4), inset 0 2px 4px rgba(255, 255, 255, 0.4), inset 0 -2px 4px rgba(0, 0, 0, 0.4)"
      }}
    >
      {/* Glossy overlay sheen */}
      <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/10 to-white/25 pointer-events-none" />

      {/* Outer Glow Ring */}
      <div className="absolute inset-0 rounded-inherit border border-white/20 pointer-events-none" />

      {/* Main App Icon Graphics */}
      <div className="relative w-full h-full flex flex-col items-center justify-center p-[12%]">
        {/* PDF File Sheet Base */}
        <div 
          className="w-full h-full border-[1.5px] border-white rounded-[4px] relative flex flex-col justify-between overflow-hidden bg-[#1E3A8A]/40"
          style={{
            boxShadow: "0 2px 4px rgba(0,0,0,0.2)"
          }}
        >
          {/* Folded corner representation at the top right */}
          <div className="absolute top-0 right-0 w-2 h-2 bg-white rounded-bl-[2px]" />
          <div className="absolute top-0 right-0 w-[9px] h-[9px] border-b border-l border-white/80 bg-[#1D4ED8]" />

          {/* Top Row: PDF Title */}
          <div className="px-1 pt-0.5 border-b border-white/30 bg-[#1D4ED8]/60 flex items-center justify-center">
            <span className="text-[9px] sm:text-[10px] font-sans font-black tracking-wider text-white leading-none">
              PDF
            </span>
          </div>

          {/* Central Section: Split Layout (Left: Down Arrow & Gears, Right: Shuffle/Crossing & Briefcase) */}
          <div className="flex-1 grid grid-cols-2 divide-x divide-white/20 bg-slate-950/20">
            {/* Left Side: Downward Arrow & Interlocking Gears */}
            <div className="flex flex-col items-center justify-between p-0.5 relative">
              {/* Arrow */}
              <div className="flex flex-col items-center leading-none">
                <div className="w-[1px] h-2.5 bg-white" />
                <ArrowDown className="w-2.5 h-2.5 text-white -mt-0.5" strokeWidth={3} />
              </div>
              {/* Interlocking Gears */}
              <div className="flex items-center gap-[1px] mt-auto">
                <Settings className="w-2.5 h-2.5 text-white/90 animate-spin" style={{ animationDuration: "12s" }} strokeWidth={2.5} />
                <Settings className="w-2 h-2 text-white/80 -ml-1 animate-spin" style={{ animationDuration: "8s", animationDirection: "reverse" }} strokeWidth={2.5} />
              </div>
            </div>

            {/* Right Side: Crossing Arrows (Split/Merge) & Toolbox/Briefcase */}
            <div className="flex flex-col items-center justify-between p-0.5">
              {/* Crossing arrows representing splitter/merger */}
              <div className="flex items-center justify-center">
                <Shuffle className="w-2.5 h-2.5 text-white rotate-90" strokeWidth={3} />
              </div>
              {/* Briefcase representing utilities/toolbox */}
              <div className="mt-auto">
                <Briefcase className="w-2.5 h-2.5 text-white" strokeWidth={2.5} />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

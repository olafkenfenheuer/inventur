#!/usr/bin/env python3
"""Re-layout an ELF64 (little-endian) shared object so every PT_LOAD segment
starts on a 16 KiB boundary that is congruent to its p_vaddr, and set its
p_align to 16 KiB. Only file offsets and header bookkeeping change; segment
contents and virtual addresses stay bit-identical, so relocations, the dynamic
section and symbol tables remain valid.
"""
import struct
import sys

ALIGN = 0x4000  # 16 KiB

PT_LOAD = 1

def u16(b, o): return struct.unpack_from("<H", b, o)[0]
def u64(b, o): return struct.unpack_from("<Q", b, o)[0]
def p16(b, o, v): struct.pack_into("<H", b, o, v)
def p64(b, o, v): struct.pack_into("<Q", b, o, v)

def main(inp, outp):
    with open(inp, "rb") as f:
        data = bytearray(f.read())

    assert data[:4] == b"\x7fELF", "not an ELF"
    assert data[4] == 2, "not ELF64"
    assert data[5] == 1, "not little-endian"

    e_phoff   = u64(data, 0x20)
    e_shoff   = u64(data, 0x28)
    e_phentsz = u16(data, 0x36)
    e_phnum   = u16(data, 0x38)
    e_shentsz = u16(data, 0x3a)
    e_shnum   = u16(data, 0x3c)

    # Parse program headers (keep the base offset of each entry for write-back).
    phdrs = []
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsz
        phdrs.append({
            "base": base,
            "p_type":   struct.unpack_from("<I", data, base + 0)[0],
            "p_offset": u64(data, base + 8),
            "p_vaddr":  u64(data, base + 16),
            "p_align":  u64(data, base + 48),
        })

    # Parse section headers (only sh_offset matters for us). Keep the index,
    # not a fixed byte position: byte insertions relocate the section-header
    # table itself, so entries must be written back relative to the FINAL
    # e_shoff, not the offset they were parsed from.
    shdrs = []
    for i in range(e_shnum):
        base = e_shoff + i * e_shentsz
        shdrs.append({"idx": i, "sh_offset": u64(data, base + 24)})

    loads = sorted((p for p in phdrs if p["p_type"] == PT_LOAD),
                   key=lambda p: p["p_offset"])

    total_pad = 0
    for seg in loads:
        cur = seg["p_offset"]
        want = seg["p_vaddr"] % ALIGN
        pad = (want - (cur % ALIGN)) % ALIGN
        if pad:
            data[cur:cur] = b"\x00" * pad
            for p in phdrs:
                if p["p_offset"] >= cur:
                    p["p_offset"] += pad
            for s in shdrs:
                if s["sh_offset"] >= cur:
                    s["sh_offset"] += pad
            if e_shoff >= cur:
                e_shoff += pad
            if e_phoff >= cur:      # (phdr table is at 0x40 -> normally untouched)
                e_phoff += pad
            total_pad += pad
        seg["p_align"] = ALIGN

    # Write everything back.
    p64(data, 0x28, e_shoff)
    p64(data, 0x20, e_phoff)
    for p in phdrs:
        p64(data, p["base"] + 8, p["p_offset"])
        p64(data, p["base"] + 48, p["p_align"])
    for s in shdrs:
        p64(data, e_shoff + s["idx"] * e_shentsz + 24, s["sh_offset"])

    with open(outp, "wb") as f:
        f.write(data)

    print(f"OK: inserted {total_pad:#x} padding bytes ({total_pad} B)")
    for seg in loads:
        print(f"  LOAD off={seg['p_offset']:#08x} vaddr={seg['p_vaddr']:#08x} "
              f"align={seg['p_align']:#x} congruent={(seg['p_offset'] % ALIGN) == (seg['p_vaddr'] % ALIGN)}")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

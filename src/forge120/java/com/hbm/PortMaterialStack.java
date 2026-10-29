package com.hbm;

public record PortMaterialStack(PortMaterial material, int amount) {
    public PortMaterialStack {
        if (material == null || amount <= 0) throw new IllegalArgumentException("Invalid material stack");
    }
}

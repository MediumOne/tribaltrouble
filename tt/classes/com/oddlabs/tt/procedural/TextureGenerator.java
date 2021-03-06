package com.oddlabs.tt.procedural;

import com.oddlabs.tt.render.Texture;
import com.oddlabs.tt.resource.ResourceDescriptor;

public abstract strictfp class TextureGenerator implements ResourceDescriptor {
	protected abstract Texture[] generate();

        @Override
	public final Object newInstance() {
		return generate();
	}

        @Override
	public int hashCode() {
		return 0;
	}

        @Override
	public boolean equals(Object o) {
		return getClass().isInstance(o);
	}

}

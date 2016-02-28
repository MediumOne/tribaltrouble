package com.oddlabs.util;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/* http://home.comcast.net/~tom_forsyth/papers/fast_vert_cache_opt.html*/
public final strictfp class IndexListOptimizer {
	private final static int LRU_SIZE = 32;
	private final static float INITIAL_SCORE = .7f;
	private final static float CACHE_SCORE_POWER = 1.5f;
	private final static float VALENCE_BOOST_SCALE = 2f;
	private final static float VALENCE_BOOST_POWER = .5f;

	public static void optimize(ShortBuffer buffer) {
/*System.out.println("buffer:");
dumpBuffer(buffer);*/
		Index[] lru = new Index[LRU_SIZE];
		Map indices = new LinkedHashMap();
		Set triangles = new LinkedHashSet();
		for (int i = 0; i < buffer.remaining()/3; i++) {
			short[] index_array = new short[]{buffer.get(buffer.position() + i*3),
				buffer.get(buffer.position() + i*3 + 1), buffer.get(buffer.position() + i*3 + 2)};
			Index[] triangle_indices = new Index[index_array.length];
			for (int j = 0; j < index_array.length; j++) {
				Short index_key = index_array[j];
				Index index = (Index)indices.get(index_key);
				if (index == null) {
					index = new Index(index_array[j]);
					indices.put(index_key, index);
				}
				triangle_indices[j] = index;
			}
			triangles.add(new Triangle(triangle_indices));
		}
		int round = 0;
		Iterator it = indices.values().iterator();
		while (it.hasNext()) {
			Index index = (Index)it.next();
			index.updateScore(-1, round);
		}
		List optimal_triangle_list = new ArrayList();
		while (!triangles.isEmpty()) {
			float best_score = Float.NEGATIVE_INFINITY;
			Triangle best_triangle = null;
                    for (Index index : lru) {
                        if (index == null)
                            break;
//System.out.println("index = " + index);
for (int j = 0; j < index.triangle_list.size(); j++) {
    Triangle tri = (Triangle)index.triangle_list.get(j);
    float tri_score = tri.getScore();
    if (tri_score > best_score) {
        best_score = tri_score;
        best_triangle = tri;
    }
}
                    }
			if (best_triangle == null) {
				it = triangles.iterator();
				while (it.hasNext()) {
					Triangle tri = (Triangle)it.next();
					float tri_score = tri.getScore();
					if (tri_score > best_score) {
						best_score = tri_score;
						best_triangle = tri;
					}
				}
			}
			assert best_triangle != null;
//System.out.println("best_triangle = " + best_triangle);
//assert !optimal_triangle_list.contains(best_triangle);
			optimal_triangle_list.add(best_triangle);
			boolean success = triangles.remove(best_triangle);
			assert success;
			best_triangle.remove();
			round++;
                    for (Index index : best_triangle.indices) {
                        //System.out.println("inserting index = " + index);
                        index.round_added = round;
                        Index swap_index = index;
                        int j;
                        for (j = 0; j < lru.length; j++) {
                            Index new_swap_index = lru[j];
                            lru[j] = swap_index;
                            swap_index.updateScore(j, round);
                            swap_index = new_swap_index;
                            if (swap_index == null || swap_index == index)
                                break;
                        }
                        if (j == lru.length) {
                            assert swap_index != index;
                            swap_index.updateScore(-1, round);
                        }
                    }
		}
		int old_position = buffer.position();
		for (int i = 0; i < optimal_triangle_list.size(); i++) {
			Triangle tri = (Triangle)optimal_triangle_list.get(i);
			tri.addToBuffer(buffer);
		}
		assert !buffer.hasRemaining(): buffer.remaining();
		buffer.position(old_position);
/*System.out.println("optimized buffer:");
dumpBuffer(buffer);*/
	}

	private static void dumpBuffer(ShortBuffer buffer) {
		for (int i = 0; i < buffer.remaining(); i++)
			System.out.print(buffer.get(buffer.position() + i) + " ");
		System.out.println();
	}

	private final static strictfp class Index {
		private final List triangle_list = new ArrayList();
		private final short index;

		private float score;
		private int round_added;

		public Index(short index) {
			this.index = index;
		}

		public final void updateScore(int cache_index, int round) {
			score = 0;
			if (cache_index != -1) {
				if (round != round_added) {
					float scale = 1f/LRU_SIZE;
					score = (float)StrictMath.pow(1f - cache_index*scale, CACHE_SCORE_POWER);
				} else
					score = INITIAL_SCORE;
			}
//System.out.println("index = " + index + " | triangle_list.size() = " + triangle_list.size() + " cache_index " + cache_index + " score " + score);
			score += VALENCE_BOOST_SCALE*(float)StrictMath.pow(triangle_list.size(), -VALENCE_BOOST_POWER);
		}

		public final void add(Triangle triangle) {
//			assert !triangle_list.contains(triangle);
			triangle_list.add(triangle);
		}

		public final void remove(Triangle triangle) {
			boolean success = triangle_list.remove(triangle);
			assert success;
		}

                @Override
		public final String toString() {
			return "[index = " + index + " score = " + score + " round = " + round_added + " num_triangles = " + triangle_list.size() + "]";
		}
	}

	private final static strictfp class Triangle {
		private final Index[] indices;

/*		private float score;
*/
		public Triangle(Index[] indices) {
			this.indices = indices;
                    for (Index indice : indices) {
                        indice.add(this);
                    }
		}

/*		public final void updateScore() {
			score = 0;
			for (int i = 0; i < indices.length; i++)
				score += indices[i].score;
		}
*/
		public final float getScore() {
			float score = 0;
                    for (Index indice : indices) {
                        score += indice.score;
                    }
			return score;
		}

		public final void remove() {
                    for (Index indice : indices) {
                        indice.remove(this);
                    }
		}

		public final void addToBuffer(ShortBuffer buffer) {
                    for (Index indice : indices) {
                        buffer.put(indice.index);
                    }
		}

                @Override
		public final String toString() {
			String result = "Triangle score = " + getScore();
                    for (Index indice : indices) {
                        result += " " + indice.toString();
                    }
			return result;
		}
	}
}

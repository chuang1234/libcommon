package com.serenegiant.glutils.es3;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2019 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;

import com.serenegiant.glutils.IDrawer2D;
import com.serenegiant.glutils.IShaderDrawer2d;
import com.serenegiant.glutils.ITexture;
import com.serenegiant.glutils.GLSurface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import androidx.annotation.NonNull;

import static com.serenegiant.glutils.ShaderConst.*;

/**
 * 描画領域全面にテクスチャを2D描画するためのヘルパークラス
 */
public class GLDrawer2D implements IShaderDrawer2d {
//	private static final boolean DEBUG = false; // FIXME set false on release
//	private static final String TAG = "GLDrawer2D";

	static final float[] VERTICES = { 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f };
	static final float[] TEXCOORD = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
	static final int FLOAT_SZ = Float.SIZE / 8;

	final int VERTEX_NUM;
	final int VERTEX_SZ;
	final FloatBuffer pVertex;
	final FloatBuffer pTexCoord;
	final int mTexTarget;
	int hProgram;
    int maPositionLoc;
    int maTextureCoordLoc;
    int muMVPMatrixLoc;
    int muTexMatrixLoc;
    @NonNull
	final float[] mMvpMatrix = new float[16];

	/**
	 * コンストラクタ
	 * GLコンテキスト/EGLレンダリングコンテキストが有効な状態で呼ばないとダメ
	 * @param isOES 外部テクスチャ(GL_TEXTURE_EXTERNAL_OES)を使う場合はtrue。
	 * 				通常の2Dテキスチャならfalse
	 */
	public GLDrawer2D(final boolean isOES) {
		this(VERTICES, TEXCOORD, isOES);
	}

	/**
	 * コンストラクタ
	 * GLコンテキスト/EGLレンダリングコンテキストが有効な状態で呼ばないとダメ
	 * @param vertices 頂点座標, floatを8個 = (x,y) x 4ペア
	 * @param texcoord テクスチャ座標, floatを8個 = (s,t) x 4ペア
	 * @param isOES 外部テクスチャ(GL_TEXTURE_EXTERNAL_OES)を使う場合はtrue。
	 * 				通常の2Dテキスチャならfalse
	 */
	public GLDrawer2D(final float[] vertices,
					  final float[] texcoord, final boolean isOES) {

		VERTEX_NUM = Math.min(
			vertices != null ? vertices.length : 0,
			texcoord != null ? texcoord.length : 0) / 2;
		VERTEX_SZ = VERTEX_NUM * 2;

		mTexTarget = isOES ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
		pVertex = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex.put(vertices);
		pVertex.flip();
		pTexCoord = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pTexCoord.put(texcoord);
		pTexCoord.flip();

		if (isOES) {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE_OES);
		} else {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE);
		}
		// モデルビュー変換行列を初期化
		Matrix.setIdentityM(mMvpMatrix, 0);
		init();
	}

	/**
	 * 破棄処理。GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 */
	@Override
	public void release() {
		if (hProgram >= 0) {
			GLES30.glDeleteProgram(hProgram);
		}
		hProgram = -1;
	}

	/**
	 * 外部テクスチャを使うかどうか
	 * @return
	 */
	public boolean isOES() {
		return mTexTarget == GL_TEXTURE_EXTERNAL_OES;
	}

	/**
	 * モデルビュー変換行列を取得(内部配列を直接返すので変更時は要注意)
	 * @return
	 */
	@Override
	public float[] getMvpMatrix() {
		return mMvpMatrix;
	}

	/**
	 * モデルビュー変換行列に行列を割り当てる
	 * @param matrix 領域チェックしていないのでoffsetから16個以上必須
	 * @param offset
	 * @return
	 */
	@Override
	public IDrawer2D setMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(matrix, offset, mMvpMatrix, 0, 16);
		return this;
	}

	/**
	 * モデルビュー変換行列のコピーを取得
	 * @param matrix 領域チェックしていないのでoffsetから16個以上必須
	 * @param offset
	 */
	@Override
	public void getMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mMvpMatrix, 0, matrix, offset, 16);
	}

	/**
	 * 指定したテクスチャを指定したテクスチャ変換行列を使って描画領域全面に描画するためのヘルパーメソッド
	 * このクラスインスタンスのモデルビュー変換行列が設定されていればそれも適用された状態で描画する
	 * @param texId texture ID
	 * @param tex_matrix テクスチャ変換行列、nullならば以前に適用したものが再利用される。
	 * 					領域チェックしていないのでoffsetから16個以上確保しておくこと
	 */
	@Override
	public synchronized void draw(final int texId,
		final float[] tex_matrix, final int offset) {

//		if (DEBUG) Log.v(TAG, "draw");
		if (hProgram < 0) return;
		GLES30.glUseProgram(hProgram);
		if (tex_matrix != null) {
			// テクスチャ変換行列が指定されている時
			GLES30.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, offset);
		}
		// モデルビュー変換行列をセット
		GLES30.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
		GLES30.glBindTexture(mTexTarget, texId);
		GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
		GLES30.glBindTexture(mTexTarget, 0);
        GLES30.glUseProgram(0);
	}

	/**
	 * Textureオブジェクトを描画するためのヘルパーメソッド
	 * Textureオブジェクトで管理しているテクスチャ名とテクスチャ座標変換行列を使って描画する
	 * @param texture
	 */
	@Override
	public void draw(final ITexture texture) {
		draw(texture.getTexture(), texture.getTexMatrix(), 0);
	}

	/**
	 * TextureOffscreenオブジェクトを描画するためのヘルパーメソッド
	 * @param offscreen
	 */
	@Override
	public void draw(final GLSurface offscreen) {
		draw(offscreen.getTexture(), offscreen.getTexMatrix(), 0);
	}

	/**
	 * テクスチャ名生成のヘルパーメソッド
	 * GLHelper#initTexを呼び出すだけ
	 * @return texture ID
	 */
	public int initTex() {
		return GLHelper.initTex(mTexTarget, GLES20.GL_TEXTURE0, GLES30.GL_NEAREST);
	}

	/**
	 * テクスチャ名破棄のヘルパーメソッド
	 * GLHelper.deleteTexを呼び出すだけ
	 * @param hTex
	 */
	public void deleteTex(final int hTex) {
		GLHelper.deleteTex(hTex);
	}

	/**
	 * 頂点シェーダー・フラグメントシェーダーを変更する
	 * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 * glUseProgramが呼ばれた状態で返る
	 * @param vs 頂点シェーダー文字列
	 * @param fs フラグメントシェーダー文字列
	 */
	@Override
	public synchronized void updateShader(final String vs, final String fs) {
		release();
		hProgram = GLHelper.loadShader(vs, fs);
		init();
	}

	/**
	 * フラグメントシェーダーを変更する
	 * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 * glUseProgramが呼ばれた状態で返る
	 * @param fs フラグメントシェーダー文字列
	 */
	@Override
	public void updateShader(final String fs) {
		updateShader(VERTEX_SHADER, fs);
	}

	/**
	 * 頂点シェーダー・フラグメントシェーダーをデフォルトに戻す
	 */
	@Override
	public void resetShader() {
		release();
		if (isOES()) {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE_OES);
		} else {
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE);
		}
		init();
	}

	/**
	 * アトリビュート変数のロケーションを取得
	 * glUseProgramが呼ばれた状態で返る
	 * @param name
	 * @return
	 */
	@Override
	public int glGetAttribLocation(final String name) {
		GLES30.glUseProgram(hProgram);
		return GLES30.glGetAttribLocation(hProgram, name);
	}

	/**
	 * ユニフォーム変数のロケーションを取得
	 * glUseProgramが呼ばれた状態で返る
	 * @param name
	 * @return
	 */
	@Override
	public int glGetUniformLocation(final String name) {
		GLES30.glUseProgram(hProgram);
		return GLES30.glGetUniformLocation(hProgram, name);
	}

	/**
	 * glUseProgramが呼ばれた状態で返る
	 */
	@Override
	public void glUseProgram() {
		GLES30.glUseProgram(hProgram);
	}

	/**
	 * シェーダープログラム変更時の初期化処理
	 * glUseProgramが呼ばれた状態で返る
	 */
	private void init() {
		GLES30.glUseProgram(hProgram);
		maPositionLoc = GLES30.glGetAttribLocation(hProgram, "aPosition");
		maTextureCoordLoc = GLES30.glGetAttribLocation(hProgram, "aTextureCoord");
		muMVPMatrixLoc = GLES30.glGetUniformLocation(hProgram, "uMVPMatrix");
		muTexMatrixLoc = GLES30.glGetUniformLocation(hProgram, "uTexMatrix");
		//
		GLES30.glUniformMatrix4fv(muMVPMatrixLoc,
			1, false, mMvpMatrix, 0);
		GLES30.glUniformMatrix4fv(muTexMatrixLoc,
			1, false, mMvpMatrix, 0);
		GLES30.glVertexAttribPointer(maPositionLoc,
			2, GLES30.GL_FLOAT, false, VERTEX_SZ, pVertex);
		GLES30.glVertexAttribPointer(maTextureCoordLoc,
			2, GLES30.GL_FLOAT, false, VERTEX_SZ, pTexCoord);
		GLES30.glEnableVertexAttribArray(maPositionLoc);
		GLES30.glEnableVertexAttribArray(maTextureCoordLoc);
	}
}

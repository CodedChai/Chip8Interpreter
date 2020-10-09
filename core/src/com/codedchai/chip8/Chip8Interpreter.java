package com.codedchai.chip8;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class Chip8Interpreter extends ApplicationAdapter {

	Emulator chip8Emulator;
	Thread emulatorThread;

	public final static float WIDTH = 64;
	public final static float HEIGHT = 32;

	FitViewport viewport;
	OrthographicCamera camera;
	ShapeRenderer shapeRenderer;

	int[] pixels;

	@Override
	public void create() {
		pixels = new int[(int) (WIDTH * HEIGHT)];

		shapeRenderer = new ShapeRenderer();
		camera = new OrthographicCamera( WIDTH, HEIGHT );
		viewport = new FitViewport( WIDTH, HEIGHT, camera );
		shapeRenderer.setProjectionMatrix( viewport.getCamera().combined );

		chip8Emulator = new Emulator();
		emulatorThread = new Thread( chip8Emulator );
		emulatorThread.start();

	}

	@Override
	public void render() {
		Gdx.gl.glClearColor( 0, 0, 0, 1 );
		Gdx.gl.glClear( GL20.GL_COLOR_BUFFER_BIT );

		Gdx.gl.glEnable( GL20.GL_BLEND );
		Gdx.gl.glBlendFunc( GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA );
		Gdx.gl.glDisable( GL20.GL_BLEND );


		if ( chip8Emulator.drawFlag() ) {
			pixels = chip8Emulator.getDisplayPixels();
		}


		generateSprite();
	}

	@Override
	public void resize( int width, int height ) {
		viewport.update( width, height );
	}

	@Override
	public void dispose() {

	}

	/*
	An extremely naive approach where we just draw each pixel
	 */
	public void generateSprite() {

		for ( int yLine = 0; yLine < HEIGHT; yLine++ ) {
			for ( int xLine = 0; xLine < WIDTH; xLine++ ) {
				if ( pixels[xLine + (yLine * 64)] != 0 ) {
					shapeRenderer.setColor( Color.WHITE );
					shapeRenderer.begin( ShapeRenderer.ShapeType.Filled );
					shapeRenderer.rect( xLine, yLine, 1, 1 );
					shapeRenderer.end();
				}
			}
		}
	}
}

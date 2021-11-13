GSample : SCViewHolder {
	// PUBLIC
	var <playbackSpeed = 1.0;
	var <soundFile = nil;
	var <buffer = nil;
	var <gain = 1.0;
	var <startPos = 0;
	var <endPos = 0;

	// GUI
	var dragSink;
	var soundFileView;
	var playbackSpeedSlider;
	var gainSlider;
	var window = nil;

	*new { |parent, bounds|
		^super.new.init(parent, bounds);
	}

	free {
		if (soundFile != nil) { soundFile.close };
		if (buffer != nil) { buffer.free };
	}

	init { |parent, bounds|
		if (parent == nil) {
			window = Window.new("GSample",  Rect(0, 400, 800, 100), resizable: true);
			window.onClose = {
				this.free;
			};
			window.layout = VLayout();
			parent = window;
		};

		view = CompositeView(parent, bounds);

		soundFileView = SoundFileView(view, bounds);
		soundFileView.gridOn = false;
		soundFileView.timeCursorOn = true;
		soundFileView.drawsBoundingLines = false;
		soundFileView.mouseWheelAction = { |view, x, y, modifiers, xDelta, yDelta|
			var zoomFactor = 0.9;

			if ((soundFile != nil) && (yDelta > 0) && (soundFileView.bounds.width > 0)) {
				soundFileView.xZoom = soundFileView.xZoom * 0.9;
				soundFileView.scroll(x / soundFileView.bounds.width * (1 - zoomFactor));
			};
			if ((soundFile != nil) && (yDelta < 0) && (soundFileView.bounds.width > 0)) {
				soundFileView.xZoom = min(soundFileView.xZoom / zoomFactor, soundFile.numFrames / soundFile.sampleRate);
			};
		};

		// file loading
		soundFileView.canReceiveDragHandler_({
			true
		});
		soundFileView.receiveDragHandler_({
			var path, tempSoundFile, tempBuffer;
			path = View.currentDrag;
			tempSoundFile = SoundFile.new;
			if (tempSoundFile.openRead(path)) {
				if (tempSoundFile.numChannels == 1, {
					tempBuffer = Buffer.readChannel(Server.default, path, channels: [0, 0], action: { |b|
						this.buffer = b;
					});
				}, {
					tempBuffer = Buffer.readChannel(Server.default, path, channels: [0, 1], action: { |b|
						this.buffer = b;
						NotificationCenter.notify(this, \buffer);
					});
				});
				soundFileView.soundfile_(tempSoundFile);
				soundFileView.readWithTask(0, tempSoundFile.numFrames, doneAction: {
					if (soundFile != nil, { soundFile.close });
					soundFile = tempSoundFile;
				});
			}
		});

		// selection handling
		soundFileView.mouseUpAction = { |v|
			var startPos, endPos;
			startPos = v.selection(0)[0];
			endPos = v.selection(0)[1] + startPos;
			this.setSelection(startPos, endPos);
		};

		gainSlider = GSlider(view, initValue: gain.ampdb, spec: ControlSpec(-inf, 12, \db, default: gain.ampdb), name: "Gain (dB)").action_({ |obj, value| this.gain = value.dbamp });
		playbackSpeedSlider = GSlider(view, initValue: playbackSpeed, spec: ControlSpec(-2, 2,\lin), name: "Speed (x)").action_({ |obj, value| this.playbackSpeed = value });

		view.layout = VLayout(soundFileView, gainSlider, playbackSpeedSlider).margins_(0).spacing_(2);

		if (window !=nil) {
			window.layout.add(view);
			window.front;
		};
	}

	playbackSpeed_ { |value|
		playbackSpeed = value;
		playbackSpeedSlider.value = value;
		NotificationCenter.notify(this, \rate);
	}

	gain_ { |value|
		gain = value;
		gainSlider.value = value.ampdb;
		if (gain > 0) {
			soundFileView.yZoom = gain;
		};
		NotificationCenter.notify(this, \gain);
	}

	setSelection { |start, end|
		startPos = start;
		endPos = end;
		NotificationCenter.notify(this, \seek);
	}

	buffer_ { |b|
		if (buffer != nil, {
			buffer.free;
		});
		buffer = b;
	}
}

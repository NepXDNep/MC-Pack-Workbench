package net.mightypork.rpw.tasks.sequences;


import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import net.mightypork.rpw.Config;
import net.mightypork.rpw.library.MagicSources;
import net.mightypork.rpw.library.Sources;
import net.mightypork.rpw.library.VanillaPack;
import net.mightypork.rpw.project.Project;
import net.mightypork.rpw.project.Projects;
import net.mightypork.rpw.struct.LangEntry;
import net.mightypork.rpw.struct.PackMcmeta;
import net.mightypork.rpw.struct.SoundEntry;
import net.mightypork.rpw.struct.SoundEntryMap;
import net.mightypork.rpw.tree.assets.AssetEntry;
import net.mightypork.rpw.tree.assets.EAsset;
import net.mightypork.rpw.utils.files.FileUtils;
import net.mightypork.rpw.utils.files.ZipUtils;
import net.mightypork.rpw.utils.logging.Log;
import net.mightypork.rpw.utils.validation.StringFilter;


/**
 * Import pack as current project (assuming the project is newly created)
 * 
 * @author MightyPork
 */
public class SequencePopulateProjectFromPack extends AbstractMonitoredSequence {

	private File packFile;
	private Runnable after;
	private List<String> zipEntries;
	private ZipFile zip;
	private Project project;
	private HashSet<String> alreadyExtracted = new HashSet<String>();


	/**
	 * @param packFile file to load
	 * @param after runnable executed after it's done
	 */
	public SequencePopulateProjectFromPack(File packFile, Runnable after) {

		this.packFile = packFile;
		this.after = after;
		this.project = Projects.getActive();

	}


	@Override
	public int getStepCount() {

		return 4; // TODO
	}


	@Override
	public String getStepName(int step) {

		//@formatter:off
		switch (step) {
			case 0: return "Listing pack file";
			case 1: return "Adding custom languages";
			case 2: return "Adding custom sounds";
			case 3: return "Adding project files";
		}
		//@formatter:on

		return null;
	}


	@Override
	protected boolean step(int step) {

		//@formatter:off
		switch (step) {
			case 0: return stepListFile();
			case 1: return stepMcmetaAndLanguages();
			case 2: return stepCustomSounds();
			case 3: return stepOtherAssets();

		}
		//@formatter:on

		return false;
	}


	private boolean stepListFile() {

		try {
			zip = new ZipFile(packFile);
			zipEntries = ZipUtils.listZip(zip);
		} catch (Exception e) {
			Log.e(e);
			return false;
		}

		return true;
	}


	private boolean stepMcmetaAndLanguages() {

		File target;

		try {

			// pack icon
			ZipEntry ze_icon = zip.getEntry("pack.png");
			if (ze_icon != null) {
				target = new File(project.getProjectDirectory(), "pack.png");
				ZipUtils.extractZipEntry(zip, ze_icon, target);
				Log.f3("Extracted pack icon.");
			}


			// get title and custom languages
			ZipEntry ze_mcmeta = zip.getEntry("pack.mcmeta");
			if (ze_mcmeta != null) {
				String json_mcmeta = ZipUtils.zipEntryToString(zip, ze_mcmeta);
				PackMcmeta mcmeta = PackMcmeta.fromJson(json_mcmeta);

				if (mcmeta.pack != null) {
					String title = mcmeta.pack.description;
					if (title != null) {
						project.setTitle(title);
					}
				}

				if (mcmeta.language != null) {
					// copy custom languages
					for (Entry<String, LangEntry> entry : mcmeta.language.entrySet()) {
						String key = entry.getKey();

						String assetKey = "assets.minecraft.lang." + key;

						AssetEntry ae = new AssetEntry(assetKey, EAsset.LANG);

						if (Sources.vanilla.doesProvideAsset(ae.getKey())) {
							// vanilla language, skip (why was it there anyway??)
						} else {
							// new language
							String entryname = ae.getPath();
							ZipEntry ze_langfile = zip.getEntry(entryname);


							if (ze_langfile != null) {

								// copy lang to langs folder
								target = new File(project.getCustomLangDirectory(), key + ".lang");
								ZipUtils.extractZipEntry(zip, ze_langfile, target);

								// register in project
								// doing this here ensures there's no crap in the lang map
								project.getLangMap().put(key, entry.getValue());

								// mark as extracted
								alreadyExtracted.add(entryname);

								Log.f3("Extracted file: " + entryname);
							}
						}
					}
				}
			}

		} catch (Exception e) {
			Log.e(e);
			return false;
		}
		return false;
	}


	private boolean stepCustomSounds() {

		File target;

		try {

			// get title and custom languages
			ZipEntry ze_sounds = zip.getEntry("assets/minecraft/sounds.json");

			if (ze_sounds != null) {
				String json_sounds = ZipUtils.zipEntryToString(zip, ze_sounds);
				SoundEntryMap soundmap = SoundEntryMap.fromJson(json_sounds);

				project.setSoundMap(soundmap); // add to the project

				for (Entry<String, SoundEntry> entry : soundmap.entrySet()) {

					// got through entry sounds
					for (String s : entry.getValue().sounds) {

						// s = relative path to sound file from "sounds" directory, without suffix

						String assetKey = "assets.minecraft.sounds." + s.replace('/', '.');

						AssetEntry ae = new AssetEntry(assetKey, EAsset.SOUND);

						if (Sources.vanilla.doesProvideAsset(ae.getKey())) {
							// vanilla sound, skip
						} else {

							// new sound

							String entryname = ae.getPath();

							ZipEntry ze_soundfile = zip.getEntry(entryname);

							if (ze_soundfile != null) {

								// copy to sounds folder
								target = new File(project.getCustomSoundsDirectory(), s + ".ogg");
								ZipUtils.extractZipEntry(zip, ze_soundfile, target);

								// mark as extracted
								alreadyExtracted.add(entryname);

								Log.f3("Extracted file: " + entryname);
							}

						}
					}
				}
			}

		} catch (Exception e) {
			Log.e(e);
			return false;
		}
		return false;
	}


	private boolean stepOtherAssets() {

		File target;

		try {

			for (String s : zipEntries) {

				if (alreadyExtracted.contains(s)) continue;


				ZipEntry ze = zip.getEntry(s);

				if (ze == null) continue; // won't happen

				String s2 = FileUtils.escapeFilename(s);
				String[] parts = FileUtils.getFilenameParts(s2);
				String key = parts[0].replace('\\', '.').replace('/', '.');

				if (Sources.vanilla.doesProvideAsset(key)) {

					// override for vanilla

					target = new File(project.getProjectDirectory(), s);

					ZipUtils.extractZipEntry(zip, ze, target);
					project.setSourceForFile(key, MagicSources.PROJECT);
					alreadyExtracted.add(s);

				} else {

					// extra included file

					target = new File(project.getExtrasDirectory(), s);
					ZipUtils.extractZipEntry(zip, ze, target);
					alreadyExtracted.add(s);
				}
			}

		} catch (Exception e) {
			Log.e(e);
			return false;
		}
		return false;
	}


	@Override
	protected String getMonitorHeading() {

		return "Loading Resource Pack";
	}


	@Override
	protected void doBefore() {

		Log.f1("Loading resource pack to project");
		Log.f3("Pack file: " + packFile);
	}


	@Override
	protected void doAfter(boolean success) {

		Log.f1("Loading resource pack to project - done.");

		if (zip != null) {
			try {
				zip.close();
			} catch (IOException e) {
				// 
			}
		}

		after.run();
	}

}

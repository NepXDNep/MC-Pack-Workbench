package net.mightypork.rpw.struct;


import java.lang.reflect.Type;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;


public class FileObjectIndex {

	private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static Type type = null;

	public boolean virtual = false;
	public LinkedHashMap<String, FileObject> objects = null;


	public static Type getType() {

		if (type == null) {
			type = new TypeToken<FileObjectIndex>() {}.getType();
		}
		return type;
	}


	public static FileObjectIndex fromJson(String json) {

		return gson.fromJson(json, getType());
	}


	public String toJson() {

		return gson.toJson(this);
	}
}

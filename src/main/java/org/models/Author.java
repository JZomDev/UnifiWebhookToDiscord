package org.models;

import org.json.JSONException;
import org.json.JSONObject;

public class Author
{

	private final String name;
	private final String icon_url;

	public Author(String name, String icon_url)
	{
		this.name = name;
		this.icon_url = icon_url;
	}

	public JSONObject get()
	{
		JSONObject obj = new JSONObject();
		try
		{
			obj.put("name", this.name);
			obj.put("icon_url", this.icon_url);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
		return obj;
	}
}

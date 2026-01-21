package com.gillodaby.betterinfo;

import java.util.List;

record InfoView(
		String title,
		String titleColorHex,
		List<InfoView.Line> lines,
		String headerHint,
		String buttonText,
		String footerText,
		int pageIndex,
		int nextPageIndex,
		String commandCode
	)	{
	record Line(String text, String colorHex) {}
}

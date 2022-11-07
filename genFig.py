import sys
import pandas as pd
import os
import getopt
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import matplotlib.gridspec as gridspec
import seaborn as sns
import numpy as np

from matplotlib.pyplot import figure

from os import walk

from matplotlib.ticker import FormatStrFormatter

top_df_path=""




#Path for output file
filePath = ''
#Column needs to be sorted/plot
col = ''
#Which type of figure to be generated
figType = ''
#Input directory passing from command argument
inputDir = ''
#Input mean path passing from command argument
meanPath = ''
#Fixed group number
numGroup = 5.0
#Predefined frequencies
frequencies = [1.2, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2]
#Data range for Y axes
yRange = []
#Map of number of entries for each frequency of a group
groupCountMap = []
#Total number of elements
allNum = 0
#Threashold for each frequency
threshNum = []
#Store attributes
attr = []
#Record the number of rows that are dropped after shrinking
dropped = np.zeros(len(frequencies))
#Total value for each frequency
totalFreq = []
i = 0
#Pick top N methods in terms of aggregated energy consumption
TOPN = 5
###
dir=""
bname=""

dataFrame = pd.DataFrame()
experiment="kenan_sampling"

frequency_iteration_times = []
frequency_values = [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
def read_iteration_times():
	for frequency in range(1,13):
		iteration_file = open("%s/counter_based_sampling_iteration_times_%d" %(dir,frequency))
		lines = iteration_file.readlines()
		iteration_times = []

		for line in lines:
			line_fields = line.split(",")
			start = int(line_fields[0])
			end = int(line_fields[1])
			iteration_times.append((start,end))

		frequency_iteration_times.append(iteration_times)

	##Print iteration times
	findex = 0
	for fdata in frequency_iteration_times:
		inter_number = 1
		for iter_data in fdata:
			startt = int(iter_data[0])
			endt = int(iter_data[1])
			print("%d,%d,%d ms" %(findex,inter_number,(endt-startt)))
			inter_number = inter_number + 1
		findex = findex+1


	#print(frequency_iteration_times)



class FigAttr:
	def __init__(self, minVal, maxVal, interval):
		self.minVal = minVal
		self.maxVal = maxVal
		self.interval = interval

#Current python working directory path
def path():
	cwd = os.getcwd()
	return cwd + '/'

#Add file names
def getDataFrame(path):
	files = []
	global dataFrame
	dataFrame = pd.read_csv(path)
	return dataFrame

#Get column names
def getCol(fileName):
	with open(fileName) as f:
		fileLine = f.readline()
	return fileLine.split(",")

def readFirstMethodName(fileName):
	with open(fileName) as f:
		fileLine = f.readline()	#Read column name
		fileLine = f.readline() #Read the first row of contents
		return fileLine.split(",")[0]

#Workaround for sunflow
def readFirstMethodNameRawData(fileName):
#	with open(fileName) as f:
#		fileLine = f.readline()	#Read column name
#		fileLine = f.readline() #Read the first row of contents
#		return fileLine.split(",")[4]f`
	return 'org.sunflow.core.renderer.BucketRenderer.renderBucket'


freq_val=[0, 2201000, 2200000, 2100000, 2000000, 1900000, 1800000, 1700000, 1600000, 1500000, 1400000, 1300000, 1200000]
def verify(df):
	df = df[df["Frequency"]==1600000]
	df = df[df["MethodName"]=="org.sunflow.accel.NullAccelerator.intersect"]
	df = df["iteration"].unique()
	print(df)

## Dataframe will have three columns (MethodName, Frequency, Package)
def drawAllIters(df_p,startups):
	### Just adding a step to verify
	##Only ondemand frequency
	print("[drawAllIters]")
	print("Number of all records %d" %(df_p.shape[0]))
	ftext = open("%s/figures.tex" %(dir), "w")
	for freq in frequency_values:
		df = df_p[df_p["Frequency"]==freq]
		print("Number of records for Frequency %d %d" %(freq,df.shape[0]))
		if 1==1:
			df["MethodName"] = df["MethodName"].str.strip()
			methods = df["MethodName"].unique()

			startup_info = startups[startups["Frequency"]==freq]
			df = df.groupby(["MethodName", "iteration"]).agg({"Package": "sum"})
			df = df.reset_index()
			plt_indx = 0

			for mname in methods:
				f, axes = plt.subplots(1, 1)
				startup_method_info = startup_info[startup_info["MethodName"] == mname]
				percentage = startup_method_info["percentage"].iloc[0]
				iter = startup_method_info["iteration_method_startup"].iloc[0]
				axes.yaxis.set_major_formatter(FormatStrFormatter('%d J'))
				method_f = df[df["MethodName"]==mname]
				method_f.plot(x="iteration", y="Package", kind="bar", ax=axes)
				axes.set_title("Method: %s" % (mname))
				axes.set_xlabel("Iteration - Picked at %f - Iter %d" % (percentage, iter + 1))
				#xlabel = plt_indx+1
				#f.subplots_adjust(hspace=.65)
				file="%s/bar_%s_by_method_by_iter_freq_%s_%s_%d.png" % (dir,col,mname,bname,freq)
				latex_file = "bar_%s_by_method_by_iter_freq_%s_%s_%d.png" % (col, mname,bname, freq)
				caption="Method Energy Consumption for Frequency %d - %s" %(freq_val[freq],bname)
				latex_fig="""
				\\begin{figure}[H]
				\centering
				\includegraphics[width=0.5\\textwidth]{figures/%s}
				\caption{%s}				
				\end{figure}				
				""" %(latex_file,caption)
				file = file.replace("$","SS");
				f.savefig(file)
				latex_fig=latex_fig.replace("$","SS");
				ftext.write(latex_fig)

			#f.subplots_adjust(hspace=.65)
			#f.savefig("%s/bar_%s_by_method_by_iter_freq_%d.png" % (dir, col, freq))
	ftext.close()

def drawAllIters_no_startup(df_p):
	### Just adding a step to verify
	##Only ondemand frequency
	for freq in frequency_values:
		df = df_p[df_p["Frequency"] == freq]
		print("Number of records for Frequency %d %d" % (freq, df.shape[0]))
		filtered_frames = []
		iteration_times = frequency_iteration_times[frequency_values.index(freq)]

		df["iteration"] = -1
		iter_no = 1
		df["MethodName"] = df["MethodName"].str.strip()
		methods = df["MethodName"].unique()
		f, axes = plt.subplots(len(methods), 1, figsize=(15, 80))
		startup_info = startups[startups["Frequency"] == freq]
		# print(startup_info)
		for iteration_time in iteration_times:
			df_iteration = df[df["MethodStartupTime"] >= iteration_time[0]]
			df_iteration = df_iteration[df_iteration["MethodStartupTime"] <= iteration_time[1]]
			df_iteration["iteration"] = iter_no
			iter_no = iter_no + 1
			filtered_frames.append(df_iteration)

		df = pd.concat(filtered_frames)
		df = df.groupby(["MethodName", "iteration"]).agg({"Package": "sum"})
		df = df.reset_index()
		plt_indx = 0

		for mname in methods:
			startup_method_info = startup_info[startup_info["MethodName"] == mname]
			percentage = startup_method_info["percentage"].iloc[0]
			iter = startup_method_info["iteration_method_startup"].iloc[0]
			axes[plt_indx].yaxis.set_major_formatter(FormatStrFormatter('%d J'))
			method_f = df[df["MethodName"] == mname]
			method_f.plot(x="iteration", y="Package", kind="bar", ax=axes[plt_indx])
			axes[plt_indx].set_title("Method: %s" % (mname))
			axes[plt_indx].set_xlabel("Iteration - Picked at %f - Iter %d" % (percentage, iter + 1))
			plt_indx = plt_indx + 1

		f.subplots_adjust(hspace=.65)
		f.savefig("%s/bar_%s_by_method_by_iter_freq_%d.png" % (dir, col, freq))

def regroupByMethods(df):
	df["MethodName"] = df["MethodName"].str.strip()
	methods = df["MethodName"].str.strip().unique()
	f, axes = plt.subplots(len(methods), 1, figsize=(15, 80))
	plt_indx = 0
	df["MethodStartupTime"] = df["MethodStartupTime"].astype(int)
	ftext = open("%s/bymethod_%s.tex" % (dir,bname), "w")
	#ftext.writable("\\section{%s}" %(bname));

	for mname in methods:
			caption = "Method Consumption across Frequencues - %s" % (mname)
			latex_file = "bymethod_%s_%s.png" % (bname, mname)
			latex_fig = """
				\\begin{figure}[H]
				\centering
				\includegraphics[width=0.5\\textwidth]{figures/%s}
				\caption{%s}		
				\end{figure}				
				""" % (latex_file, caption)
			dff = df[df["MethodName"].str.strip() == mname.strip()]
			dff = dff.groupby(["MethodName","Frequency"]).agg({"Package":"sum","MethodStartupTime":"min"})
			dff = dff.reset_index()
			dff["Package"]=dff["Package"]/50.0
			df_std = dff.std()
			f, axes = plt.subplots(1, 1)
			dff.plot(x="Frequency", y="Package", kind="bar", ax=axes, yerr=df_std["Package"],title="%s-%s"%(bname,mname))
			print("Printing new figure ...")
			ftext.write(latex_fig)
			f.savefig("%s/methods/bymethod_%s_%s.png" %(dir,bname,mname))

def regroupByMethods_hot_hist(df):
	df["MethodName"] = df["MethodName"].str.strip()
	methods = df["MethodName"].str.strip().unique()
	f, axes = plt.subplots(len(methods), 1, figsize=(15, 80))
	df["MethodStartupTime"] = df["MethodStartupTime"].astype(int)
	ftext = open("%s/bymethod_hist_%s.tex" % (dir, bname), "w")
	# ftext.writable("\\section{%s}" %(bname));

	for mname in methods:
		caption = "Method StartupTime Histogram - %s" % (mname)
		latex_file = "bymethod_%s_%s.png" % (bname, mname)
		latex_fig = """
					\\begin{figure}[H]
					\centering
					\includegraphics[width=0.5\\textwidth]{figures/%s}
					\caption{%s}		
					\end{figure}				
					""" % (latex_file, caption)
		dff = df[df["MethodName"].str.strip() == mname.strip()]
		dff = dff.groupby(["MethodName", "Frequency"]).agg({"Package": "sum", "MethodStartupTime": "min"})
		dff = dff.reset_index()
		f, axes = plt.subplots(1, 1)
		dff.plot(x="Frequency", y="Package", kind="bar", ax=axes,title="%s-%s" % (bname, mname))
		ftext.write(latex_fig)
		f.savefig("%s/methods/startuptime_hist_%s_%s.png" % (dir, bname, mname))


def genBarGraph(dataFrame, col):
	global filePath
	global numGroup
	global frequencies
	global threshNum
	global totalFreq
	global attr
	# Formatting
	dataFrame['Frequency'] = dataFrame[['Frequency']].values / 1000000.0
	dataFrame = dataFrame.round({'Frequency': 1})
	topNMethodID = pickTopMethods(dataFrame, TOPN, col)
	filtered_frames = []
	print("Generating Heap Map ... Picking Top Methods")
	print(len(topNMethodID.keys()))
	for fID in topNMethodID.keys():
		print(len(topNMethodID[fID]))
		for mID in topNMethodID[fID]:
			print("Processing Frequency %f Method %d" %(fID, mID))
			filtered = dataFrame[(dataFrame['MethodID'] == mID)]
			filtered = filtered[(filtered['Frequency'] == fID)]
			filtered_frames.append(filtered)
			#print(filtered_frames)

	dataFrame = pd.concat(filtered_frames, axis=0)
	dataFrame = dataFrame.groupby(["MethodName","Frequency"]).agg({"Package":"sum","MethodStartupTime":"min"})
	f, axes = plt.subplots(11, 1,figsize=(15,80))
	plt.tight_layout()
	axes = axes.flatten()
	dataFrame =  dataFrame.reset_index()
	plt_indx = 0
	print("Number of Frequencies %d" %(len(frequencies)))
	for fID in topNMethodID.keys():
		f_dataframe = dataFrame[dataFrame["Frequency"]==fID]
		f_dataframe["MethodName"] = f_dataframe["MethodName"].str[16:]
		f_dataframe.plot(x="MethodName",y="Package",kind="bar",ax=axes[plt_indx])
		axes[plt_indx].tick_params(labelrotation=20)
		axes[plt_indx].set_title("Frequency %f" %(fID))
		plt_indx = plt_indx + 1


	#f.subplots_adjust(wspace=.5)
	f.subplots_adjust(hspace=.65)
	f.savefig("bar_%s_by.png" %(col))
	print(dataFrame)
	#quit()
	#regroupByMethods(dataFrame)


	#print(dataFrame)
	#print(dataFrame)

"""
Get top n methods in terms of aggregated energy consumption for each frequency
Return a series of top n methodIDs
"""
def pickTopMethods(df, n, criterion):
	topN = {}
	for freq in frequencies:
		dataFrame = df[(df['Frequency'] == freq)]
		value = dataFrame.groupby(['MethodID', 'Frequency'])[criterion].sum()
		methodDf = value.nlargest(n).reset_index()
		top_n_freq  = methodDf[(methodDf['Frequency'] == freq)]['MethodID']
		topN[freq]=top_n_freq

	return topN


"""
Return a series of top n methodIDs
Called by extract_top.sh
"""
def pickTopMethodsAbs(df, n, criterion):
    df = df[df["Frequency"]==0]
    df = df[df["iteration"]>=5]
    df["MethodName"]=df["MethodName"].str.replace("$$$$$",".",regex=False)
    value = df.groupby(['MethodName'])[criterion].sum()
    methodDf = value.nlargest(n).reset_index()
    methodDf.to_csv("%s_top.csv" %(criterion))


#Process the commands
def commandProcess(argv):
	global inputDir
	global figType
	global col
	global meanPath
	global top_df_path
	global dir
	global bname
	try:
		opts, args = getopt.getopt(argv, "hi:t:c:m:d:b:", ["dir=","inputDir=", "figureType", "column="])
	except getopt.GetoptError:
		print('genFig.py -t <Figure Type> -i <Input directory> -t <heatmap/linegraph>')
		sys.exit(2)
	for opt, arg in opts:
		if opt == '-h':
			print('test.py -c <centroidNum> -i <inputDir> -t <heatmap/linegraph>')
			sys.exit()
		elif opt in ("-i", "--inputDir"):
			inputDir = arg
		elif opt in ("-d", "--dir"):
			dir = arg
		elif opt in ("-m", "--topMDir"):
			top_df_path = arg
		elif opt in ("-t", "--figureType"):
			figType = arg
		elif opt in ("-c", "--column"):
			col = arg
		elif opt in ("-b","--benchmark"):
			bname = arg


def createYLegend(minVal, maxVal, interval):
	global numGroup
	global yRange
	yRange = []
	localMax = minVal
	i = 0
	groupID = 0
	#print('min is {}, max is {}'.format(minVal, maxVal))
	while True:
		rangeBegin = localMax
		localMax += interval
		preIndex = i
		if rangeBegin >= 1:
			#Floating point precision problem
			if groupID == numGroup - 1:
				yRange.append(str(round(rangeBegin, 1)) + '-' + str(round(maxVal, 1)))
				break
			else:
				yRange.append(str(round(rangeBegin, 1)) + '-' + str(round(localMax, 1)))

		elif rangeBegin < 1:
			if groupID == numGroup - 1:
				yRange.append(str(round(rangeBegin, 3)) + '-' + str(round(maxVal, 3)))
				break
			else:
				yRange.append(str(round(rangeBegin, 3)) + '-' + str(round(localMax, 3)))
		groupID += 1

	yRange = list(reversed(yRange))

def getArray(dataFrame):
	global frequencies
	for groupID in range(int(numGroup)):
		groupedDF = dataFrame[(dataFrame['groupID'] == groupID)]
		freqCount = []
		for freq in frequencies:
			freqCount.append(groupedDF[(groupedDF['Frequency'] == freq)].shape[0])

		groupCountMap.append(freqCount)
	return np.array(groupCountMap)

def getFreqDF(dataFrame):
	global frequencies
	#for freq in frequencies:
	#df = pd.DataFrame(data = dataFrame[(dataFrame['Frequency'] == 1.2)])
	#print(dataFrame.loc[dataFrame['Frequency'] == 1.2])
	#print(dataFrame.loc[dataFrame['hotMethodMin'] == 150])
	print(dataFrame)

#Set group ID for each entry
def setGroupID(dataFrame, minVal, maxVal, interval):
	global frequencies
	global dropped
	freqID = 0;
	i = 0
	data = []
	indexes = []
	if 'groupID' in dataFrame.columns:
		del dataFrame['groupID']
	for freq in frequencies:
		localMax = minVal
		groupID = 0
		localMaxIndexFreq = max(dataFrame[(dataFrame['Frequency'] == freq)].index.tolist())
		print(localMaxIndexFreq)
		while localMax < maxVal and i <= localMaxIndexFreq:
			groupCount = 0
			localMax += interval
			preIndex = i

			if groupID == numGroup - 1:
				while i <= localMaxIndexFreq and dataFrame[col].iloc[i] <= maxVal:
					#This is final group, jump out of outer iteration after this loop
#					print('data frame index {} group {} frequency {} package value is: {} localMax is {}'.format(i, groupID, freq, dataFrame[col].iloc[i], localMax ))
					i += 1
				#Drop the left entries that are not included within maxVal value
				localMax = maxVal
			else:
				while i <= localMaxIndexFreq and dataFrame[col].iloc[i] <= localMax:
					#Iterate until find out the last index of {code: freq} in post-shrinked (if it's applicable). Otherwise it's last index of
					# {code: freq} in original table
#					print('data frame index {} group {} frequency {} package value is: {} localMax is {}'.format(i, groupID, freq, dataFrame[col].iloc[i], localMax ))
					i += 1

			for j in range(preIndex, i):
				data.append(groupID)
				indexes.append(j)

			groupID += 1
		if i - 1 != localMaxIndexFreq:
			dropped[freqID] = localMaxIndexFreq - (i - 1)
			#print('end loop, i is {} end index of its frequency {} is {}'.format(i - 1, freq, localMaxIndexFreq))
		#The size of current frequency has been shrinked. Add dummy cells.
		for k in range(i, localMaxIndexFreq + 1):
			data.append(sys.maxsize)
			indexes.append(k)
		#Index of next frequency
		i = localMaxIndexFreq + 1
		freqID += 1

	dataFrame['groupID'] = pd.Series(data, indexes)


#Discard small number of cells in heatmap
def getShrinkedDF(dataFrame, minVal, maxVal, interval, maxGID, minGID, generation):
	global numGroup
	global frequencies
	global threshNum
	global attr

	if maxGID == numGroup - 1 and minGID == 0:
	#if maxGID == numGroup - 1:
		#print('maxVal after getColorCellByCol: {}, maxGID is: {}, minVal is: {}'.format(maxVal, maxGID, minVal))
		attr = [minVal, maxVal, interval]
		return

	#Record group ID of grey cells for each frequency
	maxGreyGroupID = np.zeros(len(frequencies))
	minGreyGroupID = np.zeros(len(frequencies))
	for i in range(len(frequencies)):
		j = 0
		tempMax = 0
		tempMin = sys.maxsize
		for gid in range(int(numGroup)):
			#Record group ID of grey cells (significant number of occurences)
			if len(dataFrame[(dataFrame['groupID'] == gid) & (dataFrame['Frequency'] == frequencies[i])]) > threshNum[i]:
				#print('frequency: {} group: {} value: {} is larger than threshold {}'.format(frequencies[i], gid, len(dataFrame[(dataFrame['groupID'] == gid)]), threshNum[i]))
				tempMax = max(tempMax, gid)
				tempMin = min(tempMin, gid)
				#print('tempMin is {}, frequency is: {} value: {} is larger than threshold {}'.format(tempMin, frequencies[i], len(dataFrame[(dataFrame['groupID'] == gid) & (dataFrame['Frequency'] == frequencies[i])]), threshNum[i]))

		maxGreyGroupID[i] = tempMax
		minGreyGroupID[i] = tempMin
	#Record maxVal group ID of grey cell as well as its frequency id
	maxGID = max(maxGreyGroupID)
	minGID = min(minGreyGroupID)

	#print('maxGID is {} minGID is {}'.format(maxGID, minGID))
	lowerLimit = minVal + interval * minGID
	upperLimit = lowerLimit + interval * (maxGID + 1)
	newInterval = (upperLimit - lowerLimit) / numGroup

	#Set group IDs
	setGroupID(dataFrame, lowerLimit, upperLimit, newInterval)

#	heatMapDF = recreateDF(dataFrame)
#	createYLegend(minVal, maxVal, interval)
#
#	#Generate each generation of shrinking process
#	plotFig(heatMapDF, droppedPercentDF, droppedNumDF, 'num', generation)
#	plotFig(heatMapDF, droppedPercentDF, droppedNumDF, 'percent', generation)

	getShrinkedDF(dataFrame, lowerLimit, upperLimit, newInterval, maxGID, minGID, generation + 1)

def getDroppedDF(heatMapDF):
	droppedPercentDF = pd.DataFrame()
	droppedNumDF = pd.DataFrame()

	for i in range(len(frequencies)):
		#dropPercent="{:.0%}".format((float(totalFreq[i]) - float(heatMapDF[frequencies[i]].sum())) / float(totalFreq[i]))
		dropPercent=(float(totalFreq[i]) - float(heatMapDF[frequencies[i]].sum())) / float(totalFreq[i])
		dropNum =float(totalFreq[i]) - float(heatMapDF[frequencies[i]].sum())
		droppedPercentDF[frequencies[i]] = pd.Series(dropPercent, [0])
		droppedNumDF[frequencies[i]] = pd.Series(dropNum, [0])

		droppedPercentDF.rename(index={1: 'Dropped'})
		droppedNumDF.rename(index={1: 'Dropped'})

	return droppedPercentDF, droppedNumDF

def getMeanDF(heatMapDF):
	global frequencies
	print(heatMapDF)
	meanDF = pd.DataFrame()
	for i in range(len(frequencies)):
		mean = heatMapDF[frequencies[i]].sum() / heatMapDF.shape[0]
		meanDF[frequencies[i]] = pd.Series(mean,[0])
		meanDF.rename(index={1: 'Mean'})

	return meanDF


#Recreate data frame for heatmap generation
def recreateDF(dataFrame):
	global frequencies
	global numGroup
	global threshNum
	freqCounter = [[0 for i in range(int(numGroup))] for j in range(len(frequencies))]
	for i in range(len(frequencies)):
		for gid in range(int(numGroup)):
			freqCounter[i][gid] = dataFrame[(dataFrame['groupID'] == gid) & (dataFrame['Frequency'] == frequencies[i])].shape[0]
	heatMapDF = pd.DataFrame()
	for i in range(len(frequencies)):
		heatMapDF[frequencies[i]] = pd.Series(freqCounter[i], range(len(freqCounter[i])))

	print(heatMapDF)
	return heatMapDF

def plotFig(heatMapDF, droppedPercentDF, droppedNumDF, numOrPercent, generation):
	global filePath
	global frequencies
	global threshNum
	global totalFreq
	global numGroup
	global meanPath

	meanDF = getMeanDF(heatMapDF)
	print(meanDF)
	plt.figure(figsize = (25,20))
	ax = []
	with sns.axes_style('white'):
		#Plus 2 for dropped row and average row
		gs = gridspec.GridSpec(int(numGroup) + 2, 10)
		ax.append(plt.subplot(gs[:-3, 2:-1]))
		if(numOrPercent == 'num'):
			#Dropped
			ax.append(plt.subplot(gs[-2, 2:-1]))

			#Mean
			ax.append(plt.subplot(gs[-1, 2:-1]))
		else:
			#Dropped
			ax.append(plt.subplot(gs[-1, 2:-1]))


	#Show absolute number
		if numOrPercent == 'num':

			for i in range(len(frequencies)):
				trick = threshNum[i] + 10
				cmap, norm = mcolors.from_levels_and_colors([0, threshNum[i], trick], ['white', 'grey', 'grey'], extend='max')
				sns.heatmap(heatMapDF.mask(((heatMapDF == heatMapDF) | heatMapDF.isnull()) & (heatMapDF.columns != frequencies[i])), ax=ax[0], cbar=False, annot=True, fmt='g', cmap=cmap, norm=norm)

			#Dropped row
			for i in range(len(frequencies)):
				cmap, norm = mcolors.from_levels_and_colors([0, threshNum[i], trick], ['white', 'white', 'white'], extend='max')
				sns.heatmap(droppedNumDF.mask(((droppedNumDF == droppedNumDF) | droppedNumDF.isnull()) & (droppedNumDF.columns != frequencies[i])), ax=ax[1], cbar=False, annot=True, fmt='g', cmap=cmap, norm=norm)
			#Mean row
			for i in range(len(frequencies)):
				cmap, norm = mcolors.from_levels_and_colors([0, threshNum[i], trick], ['white', 'white', 'white'], extend='max')
				sns.heatmap(meanDF.mask(((meanDF == meanDF) | meanDF.isnull()) & (meanDF.columns != frequencies[i])), ax=ax[2], cbar=False, annot=True, fmt='g', cmap=cmap, norm=norm)

		#Show percentage
		elif numOrPercent == 'percent':
			percentHeatMap = pd.DataFrame()
			for i in range(heatMapDF.shape[1]):
				percentHeatMap[frequencies[i]] = heatMapDF[frequencies[i]].values / float(totalFreq[i])

			for i in range(len(frequencies)):
				trick = threshNum[i] + 10
				cmap, norm = mcolors.from_levels_and_colors([0, threshNum[i] / float(totalFreq[i]), trick], ['white', 'grey', 'grey'], extend='max')
				sns.heatmap(percentHeatMap.mask(((percentHeatMap == percentHeatMap) | percentHeatMap.isnull()) & (percentHeatMap.columns != frequencies[i])), ax=ax[0], cbar=False, annot=True, fmt="2.1%", cmap=cmap, norm=norm)

			for i in range(len(frequencies)):
				cmap, norm = mcolors.from_levels_and_colors([0, threshNum[i], trick], ['white', 'white', 'white'], extend='max')
				sns.heatmap(droppedPercentDF.mask(((droppedPercentDF == droppedPercentDF) | droppedPercentDF.isnull()) & (droppedPercentDF.columns != frequencies[i])), ax=ax[1], cbar=False, annot=True, fmt="1.1%", cmap=cmap, norm=norm)

	# want a more natural, table-like display
	ax[0].xaxis.tick_top()

	ax[0].set_xticklabels(frequencies, minor=False)
	global yRange

	ax[0].set_yticklabels(yRange, minor=False, rotation=0)
	ax[1].set_yticklabels(['Dropped'], minor=False, rotation=0)
	ax[1].set_xticklabels([], minor=False)
	if(numOrPercent == 'num'):
		ax[2].set_yticklabels(['Mean'], minor=False, rotation=0)
		ax[2].set_xticklabels([], minor=False)

	fileName = filePath.split('.')[0]
	name = fileName.split('/')[-1]

	if generation == 0:
		ax[0].set_title('Prior Shrinking Range: Distribution of ' + col + ' while measuring ' + name, y=1.1)
	else:
		ax[0].set_title('After Shrinking Range: Distribution of ' + col + ' while measuring ' + name, y=1.1)

	cur_pos = []
#	for i in range(2):
#		cur_pos.append(ax[i].get_position())
#		ax[i].set_position([cur_pos[i].x0 + cur_pos[i].width * 0.15, cur_pos[i].y0 + cur_pos[i].height * 0.1, cur_pos[i].width * 0.75, cur_pos[i].height * 0.8])

	#plt.colorbar(heatmap)
	figName = path() + col + '_' + name + '_HeatMap_DVFS_' + numOrPercent + '_' + str(generation)
	plt.savefig(figName + ".png", format='png')

# def getMeanDF(dataFrame):
# 	global frequencies
#
# 	print(dataFrame.columns[0])
# 	meanVal = [[0 for i in range(len(frequencies))] for j in range(dataFrame.columns.size)]
# 	for i in range(6, dataFrame.columns.size):
# 		for j in range(len(frequencies)):
# 			temp = dataFrame[(dataFrame['Frequency'] == frequencies[j])]
# 			meanVal[i][j] = temp[dataFrame.columns[i]].sum() / temp[dataFrame.columns[i]].shape[0]
#
# 	meanDF = pd.DataFrame()
# 	for i in range(dataFrame.columns.size):
# 		meanDF[dataFrame.columns[i]] = pd.Series(meanVal[i], range(len(meanVal[i])))
# 	return meanDF

def genHeatMap(dataFrame, col):
	global filePath
	global numGroup
	global frequencies
	global threshNum
	global totalFreq
	global attr
	#Formatting
	dataFrame['Frequency'] = dataFrame[['Frequency']].values / 1000000.0
	dataFrame = dataFrame.round({'Frequency': 1})
	topNMethodID = pickTopMethods(dataFrame, TOPN, col)
	filtered_frames=[]
	print("Generating Heap Map ... Picking Top Methods")

	for fID in topNMethodID.keys():
		for mID in topNMethodID[fID]:
			print(type(mID))
			filtered = dataFrame[(dataFrame['MethodID'] == mID)]
			filtered = filtered[(filtered['Frequency'] == fID)]
			filtered_frames.append(filtered)

	dataFrame = pd.concat(filtered_frames, axis=0)
	print(dataFrame)
	#Choose the first method only
	#dataFrame = dataFrame[(dataFrame['MethodName'] == readFirstMethodNameRawData(filePath))]
	#print('length of freq col is {}, length of {} is {}'.format(len(dataFrame['Frequency']), col, len(dataFrame[col])))
	#Sort by given counter for each frequency
	dataFrame = dataFrame.sort_values(by = ['Frequency', col], ascending=[True, True])
	#Reset indexdataFrame[(dataFrame['Frequency'] == 1.2)]
	dataFrame = dataFrame.reset_index(drop=True)

	minVal = min(dataFrame[col])
	maxVal = max(dataFrame[col])
	interval = (maxVal - minVal) / numGroup

	#Set threshold count of entries for each frequency
	for freq in frequencies:
		threshNum.append(len(dataFrame[(dataFrame['Frequency'] == freq)]) * 0.05)
		setGroupID(dataFrame, minVal, maxVal, interval)
		heatMapDF = recreateDF(dataFrame)

	for freq in frequencies:
		totalFreq.append(heatMapDF[freq].sum())

	droppedPercentDF, droppedNumDF = getDroppedDF(heatMapDF)

	createYLegend(minVal, maxVal, interval)

	plotFig(heatMapDF, droppedPercentDF, droppedNumDF, 'num', 0)
	plotFig(heatMapDF, droppedPercentDF, droppedNumDF, 'percent', 0)


	#Biggest groupd ID of grey cell
	maxGID = 0
	minGID = sys.maxsize
	generation = 1
	getShrinkedDF(dataFrame, minVal, maxVal, interval, maxGID, minGID, generation)
	#print('After  getShrinkedDF maxVal is {}, minVal is {}'.format(attr[1], attr[0]))

	heatMapDF = recreateDF(dataFrame)
	droppedPercentDF, droppedNumDF = getDroppedDF(heatMapDF)

	createYLegend(attr[0], attr[1], attr[2])

	plotFig(heatMapDF, droppedPercentDF, droppedNumDF, 'num', 'last')
	plotFig(heatMapDF, droppedPercentDF, droppedNumDF, 'percent', 'last')
	#genSingleLineGraph(meanDF)


#def genSingleLineGraph(meanDF):
#	fig, ax = plt.subplots()
#	ax.set_title('Prior Shrinking Range: Distribution of ' + col + ' while measuring ' + name, y=1.1)
#	ax.set_ylabel(col, fontsize='medium')
#	cur_pos = ax.get_position()
#	ax.set_position([cur_pos.x0 + cur_pos.width * 0.1, cur_pos.y0 + cur_pos.height * 0.1, cur_pos.width * 0.75, cur_pos.height * 0.9])
#	meanDF.plot(x="freqCounter", y=getCol(col, legend=False, ax=ax, xticks=dataFrame['freqCounter'])
	#plt.legend([v[0] for v in meanDF['centroidNo']], loc='center left', bbox_to_anchor=(1.1, 0.5))
	#figName = path() + "data/sunflow/fig/" + col + '_' + name + '_LineGraph_DVFS_' + numOrPercent + '_last'
	#plt.savefig(figName + ".eps", format='eps')

#Generate Line graph
def genLineGraph(dataFrame):
	global filePath
	#Formatting
	dataFrame['freqCounter'] = dataFrame[['freqCounter']].values / 1000000.0
	#Choose the first method only
	dataFrame = dataFrame[(dataFrame['methodName'] == readFirstMethodName(filePath))]
	fig, ax = plt.subplots(2, sharex=False);
	fileName = filePath.split("/")[-1]
	counterName = []
	counterName.append(getCol(filePath)[3].strip())
	counterName.append(getCol(filePath)[4].strip())
	name = fileName.split('_')
	if name[0] == 'all':
		fig.suptitle('XMeans Clustering for All Attributes', fontsize = 16)
	else:
		fig.suptitle('XMeans Clustering for ' + name[0] + ' Attribute', fontsize = 16)

	ax[0].set_title(name[1] + '_' + name[2] + '_' + 'DVFS')
	cur_pos = []
	for i in range(2):
		ax[i].set_ylabel(counterName[i], fontsize='medium')
		cur_pos.append(ax[i].get_position())
		ax[i].set_position([cur_pos[i].x0 + cur_pos[i].width * 0.1, cur_pos[i].y0 + cur_pos[i].height * 0.1, cur_pos[i].width * 0.75, cur_pos[i].height * 0.9])
	dataFrame.groupby("centroidNo").plot(x="freqCounter", y=getCol(filePath)[3].strip(), legend=False, ax=ax[0], xticks=dataFrame['freqCounter'])
	dataFrame.groupby("centroidNo").plot(x="freqCounter", y=getCol(filePath)[4].strip(), ax=ax[1], xticks=dataFrame['freqCounter'])

	plt.legend([v[0] for v in dataFrame.groupby('centroidNo')['centroidNo']], loc='center left', bbox_to_anchor=(1.1, 0.5))
	figName = path() + "data/sunflow/fig/" + fileName.split(".")[0]
	plt.savefig(figName + ".jpg", format='jpg')


def export_top(dataFrame):
	global filePath
	global numGroup
	global frequencies
	global threshNum
	global totalFreq
	global attr
	# Formatting
	pickTopMethodsAbs(dataFrame, TOPN, col)


def rank_all_method(df,iter):
	filtered_frames=[]

	for freq in frequency_values:
		freq_index = frequency_values.index(freq)
		iteration_ts = frequency_iteration_times[freq_index]
		##The startup time of sixth iteration
		start_time = iteration_ts[iter][0]
		f_dataframe = df[df["Frequency"]==freq]
		f_dataframe = f_dataframe[f_dataframe["TS"] >= start_time]
		f_dataframe = f_dataframe.groupby(["MethodName", "Frequency"]).agg({"Package": "sum"})
		filtered_frames.append(f_dataframe)

	final_df = pd.concat(filtered_frames)
	final_df = final_df.reset_index()
	final_df["Package"] = final_df["Package"]/5
	print("Ranking done")
	final_df.to_csv("%s/ranking.csv" %(dir))





##after_iters : Is a 1-indexed number
## First iteration, pass 1
## Second iteration, pass 2 ...etc
## Default criterion if the package
def extract_top(df,n_methods,after_iters):
	criterion="Package"
	dff = df[df["Frequency"]==0]
	dff = dff[dff["iteration"] >= after_iters]
	print("[extract_top] Number of records in last iterations in on demand frequency : %d" %(dff.shape[0]))
	dff = dff.groupby(['MethodName'])[criterion].sum()
	methodDf = dff.nlargest(n_methods).reset_index()
	return methodDf


def calculate_method_startups(df):
	method_startups = df.groupby(["MethodName","Frequency"]).agg({"Package": "sum","MethodStartupTime":"min","iteration":"min"})
	method_startups["iteration_method_startup"]=-1
	method_startups["percentage"]=-1
	method_startups["startup_abs"] = -1
	method_startups["iter_start"] = -1
	method_startups["iter_end"]= -1
	method_startups = method_startups.reset_index()



	for indx in range(method_startups.shape[0]):
		startup = method_startups["MethodStartupTime"].iloc[indx]
		iter = method_startups["iteration"].iloc[indx]
		freq 	= method_startups["Frequency"].iloc[indx]
		lst_indx = frequency_values.index(freq)
		iteration_times = frequency_iteration_times[lst_indx]
		method_startups["iteration_method_startup"].iloc[indx] = iter
		rng = iteration_times[iter-1];
		iteration_duration = rng[1] - rng[0]
		method_startup_relative = rng[1] - startup
		percent = method_startup_relative / iteration_duration
		percent = 1 - percent
		method_startup_abs = percent + iter - 1
		method_startups["percentage"].iloc[indx] = percent
		method_startups["iter_start"].iloc[indx] = rng[0]
		method_startups["iter_end"].iloc[indx] = rng[1]
		method_startups["startup_abs"].iloc[indx] = method_startup_abs




	method_startups = method_startups.reset_index()
	method_startups.to_csv("%s/top_method_startups.csv" %(dir))

	methods = df["MethodName"].str.strip().unique()
	f, axes = plt.subplots(len(methods), 1, figsize=(15, 80))
	ftext = open("%s/startup_hist_%s.tex" % (dir, bname), "w")

	for mname in methods:
		caption = "Method Startup Histogram Across Iterations - %s" % (mname)
		latex_file = "startup_hist_%s_%s.png" % (bname, mname)
		latex_fig = """
					\\begin{figure}[H]
					\centering
					\includegraphics[width=0.5\\textwidth]{figures/%s}
					\caption{%s}		
					\end{figure}				
					""" % (latex_file, caption)
		dff = method_startups[method_startups["MethodName"].str.strip() == mname.strip()]
		dff = dff.reset_index()
		f, axes = plt.subplots(1, 1)
		dff.plot(y="startup_abs", x="Frequency", kind="bar", ax=axes,title="%s-%s" % (bname, mname))
		ftext.write(latex_fig)
		f.savefig("%s/methods/startup_hist_%s_%s.png" % (dir, bname, mname))

	return method_startups

def draw_by_frequency_digram(df,iter):
	ftext = open("%s/figures_freq.tex" % (dir), "w")

	for freq in frequency_values:
		latex_file = "%d_%s.png" % (freq,bname)
		caption = "Method Consumption by Frequency - %d - %s" % (freq_val[freq],bname)
		latex_fig = """
		\\begin{figure}[H]
		\centering
		\includegraphics[width=0.5\\textwidth]{figures/%s}
		\caption{%s}		
		\end{figure}				
		""" % (latex_file, caption)

		f, axes = plt.subplots(1)
		f_dataframe = df[df["Frequency"]==freq]
		freq_index = frequency_values.index(freq)
		iteration_ts = frequency_iteration_times[freq_index]
		start_time = iteration_ts[iter][0]
		f_dataframe = f_dataframe[f_dataframe["TS"]>=start_time]
		f_dataframe = f_dataframe.groupby(["MethodName","Frequency"]).agg({"Package": "sum","MethodStartupTime":"min"})
		f_dataframe = f_dataframe.reset_index()
		f_dataframe["Package"] = f_dataframe["Package"] / 5
		print(f_dataframe["MethodName"])
		f_dataframe["MethodName"] = f_dataframe["MethodName"].str[16:]
		f_dataframe.plot(x="MethodName",y="Package",kind="bar",ax=axes)
		axes.tick_params(axis="y",labelrotation=65)
		axes.tick_params(axis="x", labelrotation=25)
		axes.set_title("Frequency %d" %(freq_val[freq]))
		ftext.write(latex_fig)
		f.savefig("%s/%d_%s.png" %(dir,freq,bname),bbox_inches="tight")

	ftext.close()


def trace(df):
	##Tracing org.sunflow.core.accel.NullAccelerator.intersect in Frequency
	mname="org.sunflow.core.accel.NullAccelerator.intersect"
	freq=1
	df_freq = df[df["Frequency"]==freq]
	df_freq = df_freq[df_freq["MethodName"]==mname]

	findex = frequency_values.index(freq)
	iteration_times = frequency_iteration_times[findex]

	print("Number of all samples for method %s is %d" %(mname,df_freq.shape[0]))
	df_freq_1st = df_freq[df_freq["iteration"]>=3]
	print("Number of all samples for method %s is %d after first iteration" % (mname, df_freq_1st.shape[0]))




def bar_top_ondemand(dataFrame):
	global filePath
	global numGroup
	global frequencies
	global threshNum
	global totalFreq
	global attr
	global top_df_path
	# Formatting

	read_iteration_times()
	dataFrame["iteration"] = dataFrame["iteration"].astype(int)
	dataFrame["Frequency"] = dataFrame["Frequency"].astype(int)
	dataFrame["MethodStartupTime"] = dataFrame["MethodStartupTime"].astype(int)
	dataFrame["TS"] = dataFrame["MethodStartupTime"]
	dataFrame["MethodName"]=dataFrame["MethodName"].str.replace("$$$$$",".",regex=False)
	#dataFrame["Frequency"] = dataFrame["Frequency"].str.strip()
	#dataFrame["iteration"] = dataFrame["iteration"].str.strip()

	df_no_good = dataFrame[dataFrame["Package"]<0]
	if(df_no_good.shape[0]>0):
		print(bname)
		print(df_no_good.shape[0])
		print("No Gooood!")
		print(df_no_good["Package"])
		print(df_no_good["Frequency"])
		print(df_no_good["MethodStartupTime"])
		print("+++++++++++++++++++++++++++")

	#quit()
	#print(dataFrame["iteration"].unique())
	#trace(dataFrame)
	#quit()
	dataFrame = dataFrame[dataFrame["Package"] > 0]
	top_df = extract_top(dataFrame,5,5)
	dataFrame_topn = dataFrame[dataFrame["MethodName"].isin(top_df["MethodName"])]
	print("Number for records for top 5 methods across all iterations %d" %(dataFrame_topn.shape[0]))
	method_startups = calculate_method_startups(dataFrame_topn)
	rank_all_method(dataFrame,5)
	drawAllIters(dataFrame_topn, method_startups)
	dfg = dataFrame_topn.groupby(["MethodName","Frequency","iteration"]).agg({"Package":"sum","junk":"count"})
	dfg.to_csv("%s/method_count.csv" %(dir))
	regroupByMethods(dataFrame_topn)

def extract_top5(dataFrame):
	global filePath
	global numGroup
	global frequencies
	global threshNum
	global totalFreq
	global attr
	# Formatting
	#pickTopMethodsAbs(dataFrame, TOPN, col)
	df = dataFrame
	#Frequency zero is for ondemand profiling
	df = df[df["Frequency"]==0]
	df = df[df["iteration"]>=5]
	df["MethodName"]=df["MethodName"].str.replace("$$$$$",".",regex=False)
	value = df.groupby(['MethodName'])['Package'].sum()
	methodDf = value.nlargest(n).reset_index()
	methodDf.to_csv("%s_top.csv" %('Package'))

def main(argv):
	global filePath
	commandProcess(argv)
	filePath = path() + inputDir
	dataFrame = getDataFrame(filePath)
	if figType == 'line':
		genLineGraph(dataFrame)
	elif figType == 'heatmap':
		genBarGraph(dataFrame, col)
	elif figType=='export_top':
		export_top(dataFrame)
	elif figType == 'topn_bar':
		bar_top_ondemand(dataFrame)
	elif figType == 'extract_top5':
		extract_top5(dataFrame)

if __name__ == '__main__':
	main(sys.argv[1:])

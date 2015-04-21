import sys
sys.path.append('/usr/local/lib/python2.7/site-packages/')
sys.path
import graphlab as gl
from sets import Set
import numpy
import csv

timestamp = '201312'
## read file
review = gl.SFrame('/Users/zys/projects/javaworkspace/test_jython/'+timestamp+'.csv');
# review = gl.SFrame('/Users/zys/projects/javaworkspace/test_jython/out.csv')
comments = review['text']

## initial data(docs: bag of words)
bow  = gl.text_analytics.count_words(comments)
morestopwords = Set(['dont','restaurant','phoenix','isnt', 'wasnt','doesnt','youll','didnt','restaurants','youre'])
stopw = gl.text_analytics.stopwords()
sw = stopw.union(morestopwords)
docs = bow.dict_trim_by_keys(sw, exclude=True)

## find best topic number
train_data, test_data = gl.text_analytics.random_split(docs,0.2)
eva = []
tempT = [5,10,20,30,50,100]
for i in tempT:
    tm = gl.topic_model.create(train_data, num_topics=i,  num_iterations=300)
    eva.append(tm.evaluate(train_data, test_data)['perplexity'])
tNum = tempT[numpy.argmin(eva)]

## train model
m = gl.topic_model.create(docs, num_topics=tNum, num_iterations=300)

## get top 20 words for each topic
topics = m.get_topics(num_words=20)
new = topics.to_dataframe()
top = []
for k,g in new.groupby("topic"):
    mydict={}
    for i in range(len(g)):
        w = g.iloc[i,1]
        p = g.iloc[i,2]
        mydict.setdefault(w, p)
        d=sorted(mydict.items(), key=lambda mydict: mydict[1],reverse=True)
    top.append([k,d])
for i in range(len(top)):
    top[i][0]=timestamp + '-' + str(top[i][0])
headers = ['topic_id','Words']
with open('/Users/zys/projects/javaworkspace/test_jython/topic'+timestamp+'.csv','w') as f:
    f_csv = csv.writer(f)
    f_csv.writerow(headers)
    f_csv.writerows(top)

## associate topic with original reviews
pred = m.predict(docs, 'probability')
rel = [['review_id','topic_id','rank','prob']]
# start_time = time.time()
for i in range(len(pred)):
    ind = numpy.argsort(pred[i])[::-1][:3]
    val = numpy.sort(pred[i])[::-1][:3]
    for n in range(3):
        rel.append([review[i]['review_id'],ind[n],n+1,val[n]])
    # if i%1000==0:
        # print i
for i in range(1,len(rel)):
    rel[i][1]=timestamp+ '-' + str(rel[i][1])

with open('/Users/zys/projects/javaworkspace/test_jython/review_top_' + timestamp + '.csv', "wb") as f:
    writer = csv.writer(f)
    writer.writerows(rel)





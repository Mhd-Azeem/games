package com.az.animewallpaper.spatial

/** Fill small enclosed segmentation errors; retain border-connected background and large gaps. */
object MaskCleanup {
    fun fillSmallHoles(mask: BooleanArray, size: Int): BooleanArray {
        require(size > 0 && mask.size == size * size)
        val out=mask.copyOf();val seen=BooleanArray(mask.size);val queue=IntArray(mask.size)
        for(start in mask.indices) {
            if(mask[start] || seen[start]) continue
            var head=0;var tail=1;var border=false;queue[0]=start;seen[start]=true
            while(head<tail) {
                val i=queue[head++];val x=i%size;val y=i/size
                if(x==0 || y==0 || x==size-1 || y==size-1) border=true
                fun visit(j:Int) {if(!mask[j] && !seen[j]) {seen[j]=true;queue[tail++]=j}}
                if(x>0)visit(i-1);if(x<size-1)visit(i+1);if(y>0)visit(i-size);if(y<size-1)visit(i+size)
            }
            if(!border && tail<=size*size/100) for(j in 0 until tail) out[queue[j]]=true
        }
        return out
    }
}
